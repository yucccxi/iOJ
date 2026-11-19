package me.iynotuo.judge.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.iynotuo.judge.protocol.SubmissionStatus;

public record SubmissionCaseVO(Integer ordinal, SubmissionStatus status, String statusText,
                               @Schema(nullable = true) Integer timeUsedMs,
                               @Schema(nullable = true) Integer memoryUsedKb,
                               @Schema(nullable = true) String message) {
}
