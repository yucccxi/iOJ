package me.iynotuo.judge.submission.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.SubmissionStatus;

/**
 * 提交列表查询条件。
 */
@Data
public class SubmissionCriteria {

    private Long problemId;

    private Long userId;

    private SubmissionStatus status;

    private int offset;

    private int limit;
}
