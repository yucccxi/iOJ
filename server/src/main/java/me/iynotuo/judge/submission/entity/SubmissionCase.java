package me.iynotuo.judge.submission.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.SubmissionStatus;

/**
 * 单次提交中某个测试点的结果。
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
     * 不包含期望输出，避免泄露测试数据。
     */
    private String message;
}
