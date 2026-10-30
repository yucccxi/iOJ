package me.iynotuo.judge.submission.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.Language;
import me.iynotuo.judge.common.enums.SubmissionStatus;

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

    private LocalDateTime createdAt;

    private LocalDateTime judgedAt;
}
