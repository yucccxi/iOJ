package me.iynotuo.judge.judge.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Enables or disables a judge agent key.
 */
public record JudgeKeyStatusRequest(@NotNull(message = "enabled 不能为空") Boolean enabled) {
}
