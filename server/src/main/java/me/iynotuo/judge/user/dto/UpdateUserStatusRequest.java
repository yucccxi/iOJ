package me.iynotuo.judge.user.dto;

import jakarta.validation.constraints.NotNull;
import me.iynotuo.judge.common.enums.UserStatus;

public record UpdateUserStatusRequest(@NotNull(message = "状态不能为空") UserStatus status) {
}
