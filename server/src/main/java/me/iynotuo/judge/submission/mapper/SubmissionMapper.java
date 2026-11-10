package me.iynotuo.judge.submission.mapper;

import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.SubmissionStatus;
import me.iynotuo.judge.submission.entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for submissions. Remote judge agents claim work through the lease methods; the legacy
 * local judge still uses {@link #lockNextPendingId()} when the server runs in local mode.
 */
@Mapper
public interface SubmissionMapper {

    int insert(Submission submission);

    Submission findById(@Param("id") Long id);

    /**
     * Locks one submission for result processing. The caller must hold a transaction.
     */
    Submission findByIdForUpdate(@Param("id") Long id);

    List<SubmissionSummaryRow> findPage(SubmissionCriteria criteria);

    long countPage(SubmissionCriteria criteria);

    int countAccepted(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /**
     * Locks the oldest pending submission (local judge mode).
     */
    Long lockNextPendingId();

    /**
     * Locks the oldest pending submission for one of the given languages. A null or empty list means
     * "any language".
     */
    Long lockNextPendingIdForLanguages(@Param("languages") List<Language> languages);

    int markJudging(@Param("id") Long id);

    /**
     * Marks a submission as claimed by a remote judge agent.
     */
    int markClaimed(@Param("id") Long id,
                    @Param("judgeKeyId") Long judgeKeyId,
                    @Param("judgeNodeId") String judgeNodeId,
                    @Param("claimToken") String claimToken,
                    @Param("claimedAt") LocalDateTime claimedAt,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    int updateResult(Submission submission);

    /**
     * Applies a remote result only if the claim is still current. Returns 1 when applied, 0 when the
     * claim token or status no longer matches.
     */
    int applyResult(@Param("id") Long id,
                    @Param("claimToken") String claimToken,
                    @Param("status") SubmissionStatus status,
                    @Param("timeUsedMs") Integer timeUsedMs,
                    @Param("memoryUsedKb") Integer memoryUsedKb,
                    @Param("compileMessage") String compileMessage,
                    @Param("judgeMessage") String judgeMessage,
                    @Param("judgedAt") LocalDateTime judgedAt);

    /**
     * Extends the lease of a still-current claim. Returns 0 when the claim is gone.
     */
    int extendLease(@Param("id") Long id,
                    @Param("claimToken") String claimToken,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /**
     * Returns a claimed task to the queue after a graceful agent shutdown.
     */
    int releaseClaim(@Param("id") Long id, @Param("claimToken") String claimToken);

    /**
     * Marks a claimed task as a system error, used when the task data is no longer valid.
     */
    int failClaim(@Param("id") Long id,
                  @Param("claimToken") String claimToken,
                  @Param("message") String message,
                  @Param("judgedAt") LocalDateTime judgedAt);

    /**
     * Requeues expired claims that still have retry budget.
     */
    int requeueExpiredLeases(@Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    /**
     * Fails expired claims that exhausted their retry budget.
     */
    int failExpiredLeases(@Param("now") LocalDateTime now,
                          @Param("maxAttempts") int maxAttempts,
                          @Param("message") String message);

    /**
     * Resets leftover JUDGING submissions to PENDING when the service restarts (local judge mode only).
     */
    int resetJudgingToPending();
}
