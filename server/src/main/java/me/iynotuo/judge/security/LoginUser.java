package me.iynotuo.judge.security;

import me.iynotuo.judge.common.enums.UserRole;

/**
 * 放入 SecurityContext 的登录用户信息，避免每次请求都查库拿角色。
 */
public record LoginUser(Long id, String username, String nickname, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isSelf(Long userId) {
        return id != null && id.equals(userId);
    }
}
