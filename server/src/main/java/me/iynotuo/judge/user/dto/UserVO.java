package me.iynotuo.judge.user.dto;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.user.entity.User;

import java.time.LocalDateTime;

/**
 * 用户信息（不含密码哈希、邮箱等敏感字段）。
 */
public record UserVO(Long id, String username, String nickname, UserRole role, LocalDateTime createdAt) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getCreatedAt());
    }
}
