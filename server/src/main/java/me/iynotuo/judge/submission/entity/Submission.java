package me.iynotuo.judge.submission.entity;

import lombok.Data;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.SubmissionStatus;

import java.time.LocalDateTime;

/**
 * A submission. The source code can be large and is only returned to the submitter, the problem author and administrators.
 */
@Data
public class Submission {

    private Long id;

    private Long problemId;

    private Long userId;

    private Language language;

    private String code;

    private SubmissionStatus status;

    private Integer timeUsedMs;

    private Integer memoryUsedKb;

    /**
     * Compiler output, shown when compilation fails.
     */
    private String compileMessage;

    /**
     * System-level error message (for example missing test data); never contains test data.
     */
    private String judgeMessage;

    /**
     * Key that claimed the remote judging lease, or {@code null} for a legacy/local submission.
     */
    private Long judgeKeyId;

    /**
     * Node that claimed the remote judging lease, or {@code null}.
     */
    private String judgeNodeId;

    /**
     * Fencing token of the current remote claim.
     */
    private String claimToken;

    private LocalDateTime claimedAt;

    private LocalDateTime leaseExpiresAt;

    /**
     * How many times a remote judge has claimed this submission.
     */
    private Integer attemptCount;

    private LocalDateTime createdAt;

    private LocalDateTime judgedAt;
}
