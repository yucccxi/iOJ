package me.iynotuo.judge.submission.dto;

import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.SubmissionStatus;

import java.time.LocalDateTime;

public record SubmissionSummaryVO(Long id, Long problemId, String problemTitle, Long userId, String userNickname,
                                  Language language, SubmissionStatus status, String statusText,
                                  Integer timeUsedMs, Integer memoryUsedKb, LocalDateTime createdAt) {
}
