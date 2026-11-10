package me.iynotuo.judge.judge.dto;

import me.iynotuo.judge.judge.entity.JudgeKey;

import java.time.LocalDateTime;

/**
 * Judge key metadata. Never contains the plaintext key or its hash.
 */
public record JudgeKeyVO(Long id,
                         String name,
                         String prefix,
                         boolean enabled,
                         LocalDateTime expiresAt,
                         LocalDateTime lastUsedAt,
                         LocalDateTime createdAt) {

    public static JudgeKeyVO from(JudgeKey key) {
        return new JudgeKeyVO(key.getId(), key.getName(), key.getKeyPrefix(),
                Boolean.TRUE.equals(key.getEnabled()), key.getExpiresAt(),
                key.getLastUsedAt(), key.getCreatedAt());
    }
}
