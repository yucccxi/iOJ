package me.iynotuo.judge.user.mail;

import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.config.EmailCodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SMTP implementation.
 *
 * <p>Requires a {@link JavaMailSender}, which Spring Boot only creates when {@code spring.mail.host}
 * is set. That dependency is intentional: enabling the feature without a mail server should fail at
 * startup instead of failing on the first registration.</p>
 *
 * <p>The sender address must match the authenticated SMTP account: providers such as 163 reject a
 * mismatched envelope sender with {@code 553 Mail from must equal authorized user}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.email-code", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SmtpEmailCodeSender implements EmailCodeSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailCodeSender.class);

    private final JavaMailSender mailSender;
    private final EmailCodeProperties properties;

    public SmtpEmailCodeSender(JavaMailSender mailSender, EmailCodeProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String code, int ttlMinutes, EmailCodePurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(properties.getFrom())) {
            message.setFrom(properties.getFrom().trim());
        }
        message.setTo(email);
        message.setSubject(subjectFor(purpose));
        message.setText(bodyFor(purpose, code, ttlMinutes));
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // Log the technical reason, but never leak SMTP details to the client.
            log.error("验证码邮件发送失败 email={}", email, ex);
            throw BusinessException.of(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * Registration and password reset use different subjects, so the recipient can tell the two
     * mails apart at a glance.
     */
    private String subjectFor(EmailCodePurpose purpose) {
        return purpose == EmailCodePurpose.RESET_PASSWORD
                ? properties.getSubjectReset() : properties.getSubject();
    }

    /**
     * The reset mail never contains a link, only the code: a one-click link would put a credential
     * into browser history, referrer headers and mail scanners.
     */
    private String bodyFor(EmailCodePurpose purpose, String code, int ttlMinutes) {
        boolean reset = purpose == EmailCodePurpose.RESET_PASSWORD;
        String title = reset
                ? "你正在重置 iOJ 账号密码，验证码是：%s".formatted(code)
                : "你的 iOJ 注册验证码是：%s".formatted(code);
        String hint = reset
                ? "如果这不是你本人的操作，忽略本邮件即可，你的密码不会被修改。"
                : "如果这不是你本人的操作，忽略本邮件即可。";
        return """
                %s

                该验证码 %d 分钟内有效，仅可使用一次。连续输错会作废，请重新获取。
                %s
                """.formatted(title, ttlMinutes, hint);
    }
}
