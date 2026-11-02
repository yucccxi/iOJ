package me.iynotuo.judge.user.service;

import me.iynotuo.judge.user.mapper.EmailVerificationCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Records a rejected verification code attempt in a transaction of its own.
 *
 * <p>This exists as a separate bean for one reason: {@link EmailCodeService#verify} is called from
 * inside the registration transaction and signals a bad code by throwing. Anything written through
 * the caller's transaction would be rolled back together with that exception, so the attempt counter
 * would never move and the attempt limit would never trigger. {@code REQUIRES_NEW} suspends the
 * caller's transaction and commits the counter immediately.</p>
 */
@Service
public class EmailCodeAttemptRecorder {

    private final EmailVerificationCodeMapper codeMapper;

    public EmailCodeAttemptRecorder(EmailVerificationCodeMapper codeMapper) {
        this.codeMapper = codeMapper;
    }

    /**
     * Counts one wrong submission and, when the limit is reached, makes the code unusable right away
     * so even a later correct guess is rejected.
     *
     * @param codeId       the row that was checked
     * @param reachedLimit whether this failure consumed the last allowed attempt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long codeId, boolean reachedLimit) {
        codeMapper.incrementAttempts(codeId);
        if (reachedLimit) {
            codeMapper.invalidate(codeId, LocalDateTime.now());
        }
    }
}
