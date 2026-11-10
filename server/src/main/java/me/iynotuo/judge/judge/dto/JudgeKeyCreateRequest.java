package me.iynotuo.judge.judge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Creates a judge agent key. The plaintext key is returned only in the create response.
 */
public record JudgeKeyCreateRequest(
        @NotBlank(message = "key 名称不能为空")
        @Size(max = 128, message = "key 名称不能超过 128 字符")
        String name,
        LocalDateTime expiresAt
) {
}
