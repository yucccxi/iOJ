package me.iynotuo.judge.user.mapper;

import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.user.entity.EmailVerificationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * Data access for email verification codes; the SQL lives in resources/mapper/EmailVerificationCodeMapper.xml.
 */
@Mapper
public interface EmailVerificationCodeMapper {

    int insert(EmailVerificationCode code);

    /**
     * The most recently issued code for an address and purpose, regardless of its state.
     * Callers decide whether it is expired, used up or still usable.
     */
    EmailVerificationCode findLatest(@Param("email") String email,
                                     @Param("purpose") EmailCodePurpose purpose);

    /**
     * The most recent code of any purpose for an address. Used for the resend cooldown, so a client
     * cannot alternate between registration and password reset to bypass it.
     */
    EmailVerificationCode findLatestSent(@Param("email") String email);

    /**
     * Codes issued since a point in time, of any purpose. The hourly quota belongs to the mailbox
     * (and to the SMTP account behind it), not to a single flow.
     */
    long countSince(@Param("email") String email, @Param("since") LocalDateTime since);

    /**
     * Counts one wrong submission. Returns the number of updated rows.
     */
    int incrementAttempts(@Param("id") Long id);

    /**
     * Makes a code unusable immediately by moving its expiry into the past. Also used when the
     * attempt limit is reached, so a correct code submitted later still fails.
     */
    int invalidate(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Marks a code as used. Returns 0 when it was already consumed, which is how a replay is detected.
     */
    int markConsumed(@Param("id") Long id, @Param("now") LocalDateTime now);

    int deleteById(@Param("id") Long id);

    /**
     * Removes old rows so the table does not grow forever.
     */
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
