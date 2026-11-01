package me.iynotuo.judge.user.dto;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.user.entity.User;

import java.time.LocalDateTime;

/**
 * Public profile information, including how many problems the user has solved and contributed.
 */
public record UserProfileVO(Long id, String username, String nickname, UserRole role,
                            LocalDateTime createdAt, long solvedCount, long contributedCount) {

    public static UserProfileVO of(User user, long solvedCount, long contributedCount) {
        return new UserProfileVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getCreatedAt(), solvedCount, contributedCount);
    }
}
