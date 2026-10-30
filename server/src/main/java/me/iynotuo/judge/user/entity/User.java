package me.iynotuo.judge.user.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.common.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * 用户表映射。
 */
@Data
public class User {

    private Long id;

    private String username;

    private String email;

    /**
     * BCrypt 哈希，任何情况下都不能返回给前端。
     */
    private String passwordHash;

    private String nickname;

    private UserRole role;

    private UserStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;
}
