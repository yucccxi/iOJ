package me.iynotuo.judge.submission.dto;

import me.iynotuo.judge.common.enums.Language;
import me.iynotuo.judge.common.enums.SubmissionStatus;

import java.time.LocalDateTime;

public record SubmissionSummaryVO(Long id, Long problemId, String problemTitle, Long userId, String userNickname,
                                  Language language, SubmissionStatus status, String statusText,
                                  Integer timeUsedMs, Integer memoryUsedKb, LocalDateTime createdAt) {
}
