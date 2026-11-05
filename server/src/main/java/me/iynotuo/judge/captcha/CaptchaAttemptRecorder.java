package me.iynotuo.judge.captcha;

import me.iynotuo.judge.captcha.mapper.CaptchaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Writes CAPTCHA state in its own transaction, so a failed login/registration cannot roll back the
 * "wrong answer counted" or "challenge consumed" update and make a challenge replayable.
 */
@Service
public class CaptchaAttemptRecorder {

    private final CaptchaMapper captchaMapper;

    public CaptchaAttemptRecorder(CaptchaMapper captchaMapper) {
        this.captchaMapper = captchaMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String id, LocalDateTime invalidatedAt) {
        captchaMapper.incrementAttempts(id, invalidatedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markConsumed(String id, LocalDateTime consumedAt) {
        return captchaMapper.markConsumed(id, consumedAt) > 0;
    }
}
