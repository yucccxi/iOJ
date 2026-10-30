package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotNull;
import me.iynotuo.judge.common.enums.ProblemStatus;

/**
 * 管理员审核/上下架题目。
 */
public record UpdateProblemStatusRequest(@NotNull(message = "状态不能为空") ProblemStatus status) {
}
