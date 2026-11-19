package me.iynotuo.judge.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.SubmissionStatus;

import java.time.LocalDateTime;

public record SubmissionSummaryVO(Long id, Long problemId, String problemTitle, Long userId, String userNickname,
                                  Language language, SubmissionStatus status, String statusText,
                                  @Schema(nullable = true) Integer timeUsedMs,
                                  @Schema(nullable = true) Integer memoryUsedKb,
                                  LocalDateTime createdAt) {
}
