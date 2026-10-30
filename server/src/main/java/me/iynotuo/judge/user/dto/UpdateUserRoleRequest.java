package me.iynotuo.judge.user.dto;

import jakarta.validation.constraints.NotNull;
import me.iynotuo.judge.common.enums.UserRole;

public record UpdateUserRoleRequest(@NotNull(message = "角色不能为空") UserRole role) {
}
