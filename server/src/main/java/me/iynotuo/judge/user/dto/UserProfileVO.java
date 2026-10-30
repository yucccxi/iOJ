package me.iynotuo.judge.user.dto;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.user.entity.User;

import java.time.LocalDateTime;

/**
 * 公开的个人主页信息，额外带上通过题目数。
 */
public record UserProfileVO(Long id, String username, String nickname, UserRole role,
                            LocalDateTime createdAt, long solvedCount) {

    public static UserProfileVO of(User user, long solvedCount) {
        return new UserProfileVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getCreatedAt(), solvedCount);
    }
}
