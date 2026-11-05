package me.iynotuo.judge.captcha;

import me.iynotuo.judge.captcha.dto.CaptchaVO;
import me.iynotuo.judge.captcha.entity.CaptchaChallenge;
import me.iynotuo.judge.captcha.mapper.CaptchaMapper;
import me.iynotuo.judge.common.enums.CaptchaPurpose;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.config.CaptchaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Issues, checks and cleans up image CAPTCHA challenges.
 *
 * <p>A challenge is purpose-bound, short-lived, single-use and attempt-limited. Issuance is also
 * limited per client IP, using the challenge table itself as the ledger, so no extra counter store is
 * needed at the current scale.</p>
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);
    private static final String UNKNOWN_IP = "unknown";

    private final CaptchaMapper captchaMapper;
    private final CaptchaProperties properties;
    private final CaptchaCodeGenerator codeGenerator;
    private final CaptchaImageGenerator imageGenerator;
    private final CaptchaAttemptRecorder attemptRecorder;

    public CaptchaService(CaptchaMapper captchaMapper,
                          CaptchaProperties properties,
                          CaptchaCodeGenerator codeGenerator,
                          CaptchaImageGenerator imageGenerator,
                          CaptchaAttemptRecorder attemptRecorder) {
        this.captchaMapper = captchaMapper;
        this.properties = properties;
        this.codeGenerator = codeGenerator;
        this.imageGenerator = imageGenerator;
        this.attemptRecorder = attemptRecorder;
    }

    /**
     * Issues one challenge for the requested action.
     *
     * @param purpose  action the challenge will authorize
     * @param clientIp real client address used for the per-IP issuance limit
     */
    public CaptchaVO issue(CaptchaPurpose purpose, String clientIp) {
        if (!properties.isEnabled()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "图形验证码未启用");
        }
        String ip = normalizeIp(clientIp);
        LocalDateTime now = LocalDateTime.now();
        enforceIssueLimit(ip, now);

        String id = UUID.randomUUID().toString().replace("-", "");
        String code = codeGenerator.generate(properties.getCodeLength());

        CaptchaChallenge challenge = new CaptchaChallenge();
        challenge.setId(id);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(hash(id, code));
        challenge.setAttempts(0);
        challenge.setExpiresAt(now.plusSeconds(properties.getTtlSeconds()));
        challenge.setClientIp(ip);
        challenge.setCreatedAt(now);
        captchaMapper.insert(challenge);

        return new CaptchaVO(id, imageGenerator.generate(code), properties.getTtlSeconds());
    }

    /**
     * Validates and consumes a challenge. A correct answer consumes it immediately; a wrong answer
     * increments the attempt counter and invalidates the challenge on the last allowed attempt.
     *
     * <p>This method deliberately does not own a transaction. The state writes go through
     * {@link CaptchaAttemptRecorder} with {@code REQUIRES_NEW}, so they survive the rollback of the
     * surrounding login/registration transaction.</p>
     *
     * @param captchaId       challenge id returned by {@link #issue(CaptchaPurpose, String)}
     * @param rawCode         user input; trimmed and uppercased before comparison
     * @param expectedPurpose purpose hard-coded by the calling endpoint
     */
    public void verifyAndConsume(String captchaId, String rawCode, CaptchaPurpose expectedPurpose) {
        if (!properties.isEnabled()) {
            return;
        }
        if (captchaId == null || captchaId.isBlank() || rawCode == null || rawCode.isBlank()) {
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "请输入图形验证码");
        }

        String id = captchaId.trim();
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        CaptchaChallenge challenge = captchaMapper.findById(id);

        if (challenge == null) {
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "图形验证码不存在，请重新获取");
        }
        if (!Objects.equals(challenge.getPurpose(), expectedPurpose)) {
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "图形验证码与当前操作不匹配，请重新获取");
        }
        if (challenge.getConsumedAt() != null) {
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "图形验证码已被使用，请重新获取");
        }
        if (!challenge.getExpiresAt().isAfter(now)) {
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "图形验证码已过期，请重新获取");
        }
        if (challenge.getAttempts() >= properties.getMaxAttempts()) {
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "图形验证码尝试次数过多，请重新获取");
        }

        if (!constantTimeEquals(challenge.getCodeHash(), hash(id, code))) {
            int nextAttempts = challenge.getAttempts() + 1;
            boolean invalidate = nextAttempts >= properties.getMaxAttempts();
            attemptRecorder.recordFailure(id, invalidate ? now : null);
            if (invalidate) {
                throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "图形验证码尝试次数过多，请重新获取");
            }
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID,
                    "图形验证码错误，还可尝试 " + (properties.getMaxAttempts() - nextAttempts) + " 次");
        }

        if (!attemptRecorder.markConsumed(id, now)) {
            throw BusinessException.of(ErrorCode.CAPTCHA_INVALID, "图形验证码已被使用，请重新获取");
        }
    }

    /**
     * Deletes challenges that expired long ago, keeping the table bounded while preserving recent
     * rows for diagnostics and the hourly issuance window.
     */
    @Scheduled(fixedDelayString = "${ioj.captcha.cleanup-interval-ms:3600000}")
    public void cleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusHours(properties.getCleanupRetentionHours());
        int removed = captchaMapper.deleteExpiredBefore(before);
        if (removed > 0) {
            log.info("清理过期图形验证码 {} 条", removed);
        }
    }

    private void enforceIssueLimit(String clientIp, LocalDateTime now) {
        int minuteCount = captchaMapper.countByIpSince(clientIp, now.minusMinutes(1));
        if (minuteCount >= properties.getIssuePerMinutePerIp()) {
            throw BusinessException.of(ErrorCode.TOO_MANY_REQUESTS,
                    "图形验证码获取过于频繁，请稍后再试");
        }
        int hourCount = captchaMapper.countByIpSince(clientIp, now.minusHours(1));
        if (hourCount >= properties.getIssuePerHourPerIp()) {
            throw BusinessException.of(ErrorCode.TOO_MANY_REQUESTS,
                    "图形验证码获取过于频繁，请稍后再试");
        }
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return UNKNOWN_IP;
        }
        String value = clientIp.trim();
        return value.length() <= 45 ? value : value.substring(0, 45);
    }

    private String hash(String id, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest((id + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
