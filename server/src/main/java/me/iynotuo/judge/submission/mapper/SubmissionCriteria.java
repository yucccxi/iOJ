package me.iynotuo.judge.submission.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.SubmissionStatus;

/**
 * Query criteria for submission lists.
 */
@Data
public class SubmissionCriteria {

    private Long problemId;

    private Long userId;

    private SubmissionStatus status;

    private int offset;

    private int limit;
}
