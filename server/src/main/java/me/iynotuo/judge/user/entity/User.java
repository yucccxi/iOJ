package me.iynotuo.judge.user.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.common.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * User table mapping.
 */
@Data
public class User {

    private Long id;

    private String username;

    private String email;

    /**
     * BCrypt hash. It must never be returned to clients.
     */
    private String passwordHash;

    private String nickname;

    private UserRole role;

    private UserStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;
}
