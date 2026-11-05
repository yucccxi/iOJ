package me.iynotuo.judge.captcha.mapper;

import me.iynotuo.judge.captcha.entity.CaptchaChallenge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * CAPTCHA challenge storage and the small counters used by per-IP issuance limiting.
 */
@Mapper
public interface CaptchaMapper {

    int insert(CaptchaChallenge challenge);

    CaptchaChallenge findById(@Param("id") String id);

    /**
     * Number of challenges issued to one IP since the given instant.
     */
    int countByIpSince(@Param("clientIp") String clientIp, @Param("since") LocalDateTime since);

    /**
     * Increments the wrong-answer counter. When {@code invalidatedAt} is non-null the challenge is
     * also consumed, so it cannot be used even with the correct answer.
     */
    int incrementAttempts(@Param("id") String id, @Param("invalidatedAt") LocalDateTime invalidatedAt);

    /**
     * Atomically consumes a challenge.
     *
     * @return 1 when this call consumed it, 0 when another request already did
     */
    int markConsumed(@Param("id") String id, @Param("consumedAt") LocalDateTime consumedAt);

    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
