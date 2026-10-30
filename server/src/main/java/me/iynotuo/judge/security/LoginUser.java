package me.iynotuo.judge.security;

import me.iynotuo.judge.common.enums.UserRole;

/**
 * Authenticated user stored in the SecurityContext, so controllers can read the current user without extra lookups.
 */
public record LoginUser(Long id, String username, String nickname, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isSelf(Long userId) {
        return id != null && id.equals(userId);
    }
}
