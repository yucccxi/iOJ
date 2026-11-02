package me.iynotuo.judge.user.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.EmailCodePurpose;

import java.time.LocalDateTime;

/**
 * Email verification code row mapping.
 *
 * <p>Every send request inserts a new row; the newest row for an (email, purpose) pair is the only
 * one that can be used. {@code consumedAt} non-null means the code has already been used, and
 * {@code attempts} counts how often a wrong code was submitted for it.</p>
 */
@Data
public class EmailVerificationCode {

    private Long id;

    /**
     * Lower-cased, trimmed address. Codes are never keyed by a raw request value.
     */
    private String email;

    private EmailCodePurpose purpose;

    /**
     * The plain 6-digit code. It is short lived and single use, so it is not hashed:
     * a hash of six digits is brute-forceable anyway.
     */
    private String code;

    private Integer attempts;

    private LocalDateTime expiresAt;

    /**
     * Set when the code was used successfully, so it can never be replayed.
     */
    private LocalDateTime consumedAt;

    private LocalDateTime createdAt;
}
