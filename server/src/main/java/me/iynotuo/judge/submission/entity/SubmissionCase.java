package me.iynotuo.judge.submission.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.SubmissionStatus;

/**
 * The result of one test case within a submission.
 */
@Data
public class SubmissionCase {

    private Long id;

    private Long submissionId;

    private Integer caseOrdinal;

    private SubmissionStatus status;

    private Integer timeUsedMs;

    private Integer memoryUsedKb;

    /**
     * Never contains the expected output, to avoid leaking test data.
     */
    private String message;
}
