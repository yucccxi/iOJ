package me.iynotuo.judge.user.dto;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.user.entity.User;

import java.time.LocalDateTime;

/**
 * Returned after a successful registration.
 *
 * <p>The user fields have the same shape as {@link UserVO}, while the token fields match
 * {@link LoginResponse}, so a client can start using the account immediately.</p>
 */
public record RegisterResponse(Long id,
                               String username,
                               String nickname,
                               UserRole role,
                               LocalDateTime createdAt,
                               String token,
                               String tokenType,
                               long expiresIn) {

    public static RegisterResponse of(User user, String token, long expiresInSeconds) {
        return new RegisterResponse(user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getCreatedAt(), token, "Bearer", expiresInSeconds);
    }
}
