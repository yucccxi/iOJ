package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotNull;
import me.iynotuo.judge.common.enums.ProblemStatus;

/**
 * Administrator review: publish or take down a problem.
 */
public record UpdateProblemStatusRequest(@NotNull(message = "状态不能为空") ProblemStatus status) {
}
