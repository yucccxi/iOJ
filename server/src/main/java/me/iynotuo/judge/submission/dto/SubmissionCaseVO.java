package me.iynotuo.judge.submission.dto;

import me.iynotuo.judge.protocol.SubmissionStatus;

public record SubmissionCaseVO(Integer ordinal, SubmissionStatus status, String statusText,
                               Integer timeUsedMs, Integer memoryUsedKb, String message) {
}
