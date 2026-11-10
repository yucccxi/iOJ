package me.iynotuo.judge.judge.dto;

import me.iynotuo.judge.judge.service.JudgeKeyService;

import java.time.LocalDateTime;

/**
 * Response of key creation. The {@code key} field is the only time the plaintext value is exposed.
 */
public record JudgeKeyCreatedVO(Long id,
                                String name,
                                String prefix,
                                String key,
                                LocalDateTime expiresAt,
                                LocalDateTime createdAt) {

    public static JudgeKeyCreatedVO from(JudgeKeyService.CreatedKey created) {
        return new JudgeKeyCreatedVO(created.key().getId(), created.key().getName(),
                created.key().getKeyPrefix(), created.plaintext(),
                created.key().getExpiresAt(), created.key().getCreatedAt());
    }
}
