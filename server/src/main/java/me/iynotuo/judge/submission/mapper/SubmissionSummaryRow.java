package me.iynotuo.judge.submission.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Language;
import me.iynotuo.judge.common.enums.SubmissionStatus;

import java.time.LocalDateTime;

/**
 * Database row for a submission list (problem title and user nickname joined in).
 */
@Data
public class SubmissionSummaryRow {

    private Long id;

    private Long problemId;

    private String problemTitle;

    private Long userId;

    private String userNickname;

    private Language language;

    private SubmissionStatus status;

    private Integer timeUsedMs;

    private Integer memoryUsedKb;

    private LocalDateTime createdAt;
}
