package me.iynotuo.judge.judge.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API key used by a remote judge agent. Only the hash is stored in the database; the plaintext value is
 * returned once when an administrator creates the key.
 */
@Data
public class JudgeKey {

    private Long id;

    private String name;

    /**
     * Short prefix such as {@code jsk_3f9a2c}, safe to show in lists and logs.
     */
    private String keyPrefix;

    /**
     * SHA-256 hex digest of the full key.
     */
    private String keyHash;

    private Boolean enabled;

    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    private Long createdBy;

    private LocalDateTime createdAt;
}
