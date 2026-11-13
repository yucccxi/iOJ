package me.iynotuo.judge.user.service;

import me.iynotuo.judge.captcha.CaptchaService;
import me.iynotuo.judge.common.enums.CaptchaPurpose;
import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.common.enums.UserStatus;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.config.EmailCodeProperties;
import me.iynotuo.judge.user.dto.SendEmailCodeVO;
import me.iynotuo.judge.user.entity.EmailVerificationCode;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mail.EmailCodeSender;
import me.iynotuo.judge.user.mapper.EmailVerificationCodeMapper;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Issues and checks email verification codes.
 *
 * <p>Each send request inserts a row, so the history doubles as the rate-limit ledger: the newest
 * row decides the resend cooldown, and the row count in the last hour decides the hourly cap. Codes
 * are short lived and single use, and reaching the attempt limit invalidates the row immediately.</p>
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationCodeMapper codeMapper;
    private final UserMapper userMapper;
    private final EmailCodeSender sender;
    private final EmailCodeProperties properties;
    private final EmailCodeAttemptRecorder attemptRecorder;
    private final CaptchaService captchaService;

    public EmailCodeService(EmailVerificationCodeMapper codeMapper,
                            UserMapper userMapper,
                            EmailCodeSender sender,
                            EmailCodeProperties properties,
                            EmailCodeAttemptRecorder attemptRecorder,
                            CaptchaService captchaService) {
        this.codeMapper = codeMapper;
        this.userMapper = userMapper;
        this.sender = sender;
        this.properties = properties;
        this.attemptRecorder = attemptRecorder;
        this.captchaService = captchaService;
    }

    /**
     * Whether email verification codes are enabled, for both registration and password reset.
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Issues a code and sends it by mail.
     *
     * <p>Not transactional on purpose: the mail goes out between two statements, so wrapping the
     * call in a database transaction would only hold a connection for the duration of an SMTP
     * round trip. A failed send deletes the row it just wrote.</p>
     *
     * @param rawEmail    the address typed by the visitor
     * @param purpose     {@code REGISTER} needs an unused address, {@code RESET_PASSWORD} needs an
     *                    existing active account
     * @param captchaId   CAPTCHA challenge id, when CAPTCHA is enabled
     * @param captchaCode CAPTCHA answer, when CAPTCHA is enabled
     * @return how long the code is valid and when another one may be requested
     */
    public SendEmailCodeVO send(String rawEmail, EmailCodePurpose purpose,
                                String captchaId, String captchaCode) {
        if (!properties.isEnabled()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "邮箱验证功能未启用");
        }
        // Check the human challenge before looking at the mailbox, so this endpoint cannot be used
        // to probe which addresses are registered without solving a CAPTCHA first.
        captchaService.verifyAndConsume(captchaId, captchaCode, CaptchaPurpose.EMAIL_SENDING);
        String email = normalize(rawEmail);
        requireEligibleRecipient(email, purpose);

        LocalDateTime now = LocalDateTime.now();
        // The hourly quota is checked first: it is the longer-term rule, and a client that already
        // used up its quota should be told so instead of being asked to wait for the cooldown.
        // It counts every purpose, because what the quota protects is the mailbox and the sending
        // reputation of the SMTP account, not one particular flow.
        long sentInLastHour = codeMapper.countSince(email, now.minusHours(1));
        if (sentInLastHour >= properties.getMaxPerHour()) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_TOO_FREQUENT,
                    "该邮箱一小时内最多发送 " + properties.getMaxPerHour() + " 次验证码，请稍后再试");
        }
        EmailVerificationCode latest = codeMapper.findLatestSent(email);
        if (latest != null && latest.getCreatedAt().isAfter(now.minusSeconds(properties.getResendIntervalSeconds()))) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_TOO_FREQUENT,
                    "验证码已发送，请 " + properties.getResendIntervalSeconds() + " 秒后再试");
        }

        EmailVerificationCode row = new EmailVerificationCode();
        row.setEmail(email);
        row.setPurpose(purpose);
        row.setCode(randomCode());
        row.setAttempts(0);
        row.setExpiresAt(now.plusMinutes(properties.getTtlMinutes()));
        row.setCreatedAt(now);
        codeMapper.insert(row);

        try {
            sender.send(email, row.getCode(), properties.getTtlMinutes(), purpose);
        } catch (RuntimeException ex) {
            // The recipient never saw this code, so it must not stay usable: keeping it would also
            // burn one slot of the hourly quota for a mail that does not exist.
            codeMapper.deleteById(row.getId());
            throw ex;
        }
        log.info("验证码已发送 email={} purpose={} expiresAt={}", email, purpose, row.getExpiresAt());
        return new SendEmailCodeVO(properties.getTtlMinutes() * 60L, properties.getResendIntervalSeconds());
    }

    /**
     * Rejects an address the given flow cannot serve.
     *
     * <p>The error is explicit (2003 for registration, 2001/2004 for reset) because the caller needs
     * to tell the user what to do; the same information is already available from
     * {@code POST /auth/register}, so this does not create a new way to probe for accounts.</p>
     */
    private void requireEligibleRecipient(String email, EmailCodePurpose purpose) {
        User user = userMapper.findByEmail(email);
        if (purpose == EmailCodePurpose.REGISTER) {
            if (user != null) {
                throw BusinessException.of(ErrorCode.EMAIL_TAKEN);
            }
            return;
        }
        if (user == null) {
            throw BusinessException.of(ErrorCode.USER_NOT_FOUND, "该邮箱尚未注册");
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw BusinessException.of(ErrorCode.USER_DISABLED);
        }
    }

    /**
     * Checks a code and returns its row id, without consuming it.
     *
     * <p>Two transaction concerns shape this method:</p>
     * <ul>
     *   <li>The caller performs the protected write (account insert or password update) inside its own
     *   transaction, and a rejected code must still count as an attempt even though that outer
     *   transaction is rolled back by the exception. The counter therefore goes through
     *   {@link EmailCodeAttemptRecorder}, whose {@code REQUIRES_NEW} transaction commits on its own.</li>
     *   <li>The success path does not consume the row here, because the caller's write could still
     *   fail (for example on a duplicate key).</li>
     * </ul>
     *
     * @return the id to pass to {@link #consume(Long)} after the protected write succeeds
     */
    public Long verify(String rawEmail, EmailCodePurpose purpose, String inputCode) {
        String email = normalize(rawEmail);
        String code = inputCode == null ? "" : inputCode.trim();
        if (code.isEmpty()) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID, "邮箱验证码不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        EmailVerificationCode row = codeMapper.findLatest(email, purpose);
        if (row == null) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID);
        }
        if (row.getConsumedAt() != null) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID, "该验证码已被使用，请重新获取");
        }
        // Checked before the expiry test: an exhausted code is also expired, and the attempt limit is
        // the reason the visitor needs to know about.
        if (row.getAttempts() >= properties.getMaxAttempts()) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID, "验证码尝试次数过多，请重新获取");
        }
        if (!row.getExpiresAt().isAfter(now)) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID, "邮箱验证码已过期，请重新获取");
        }

        if (!constantTimeEquals(row.getCode(), code)) {
            int remaining = properties.getMaxAttempts() - (row.getAttempts() + 1);
            attemptRecorder.recordFailedAttempt(row.getId(), remaining <= 0);
            if (remaining <= 0) {
                // Reaching the limit invalidates the code, so even the right code is rejected later.
                throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID, "验证码尝试次数过多，请重新获取");
            }
            throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID, "邮箱验证码错误，还可尝试 " + remaining + " 次");
        }
        return row.getId();
    }

    /**
     * Marks a code as used. Call it inside the transaction that performs the protected write (account
     * insert or password update), so a failed write leaves the code valid and a successful one can
     * never be replayed.
     */
    public void consume(Long codeId) {
        if (codeId == null) {
            return;
        }
        if (codeMapper.markConsumed(codeId, LocalDateTime.now()) == 0) {
            throw BusinessException.of(ErrorCode.EMAIL_CODE_INVALID, "该验证码已被使用，请重新获取");
        }
    }

    /**
     * Removes rows that expired long ago, keeping the table small without touching the hourly quota
     * of the current day.
     */
    @Scheduled(fixedDelayString = "${ioj.email-code.cleanup-interval-ms:3600000}")
    public void cleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusHours(properties.getCleanupRetentionHours());
        int removed = codeMapper.deleteExpiredBefore(before);
        if (removed > 0) {
            log.info("清理过期验证码 {} 条", removed);
        }
    }

    /**
     * Trims and lower-cases an address, so one mailbox always maps to the same rows. MySQL compares
     * case-insensitively while H2 does not, so normalizing keeps both behaving the same.
     */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String randomCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
