package me.iynotuo.judge.user.mail;

import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Placeholder used when {@code ioj.email-code.enabled=false}.
 *
 * <p>Its only job is to let the application start without an SMTP server. The send endpoint is
 * already closed in that configuration, so this should never actually deliver anything.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.email-code", name = "enabled", havingValue = "false")
public class DisabledEmailCodeSender implements EmailCodeSender {

    @Override
    public void send(String email, String code, int ttlMinutes, EmailCodePurpose purpose) {
        throw BusinessException.of(ErrorCode.BAD_REQUEST, "邮箱验证功能未启用");
    }
}
