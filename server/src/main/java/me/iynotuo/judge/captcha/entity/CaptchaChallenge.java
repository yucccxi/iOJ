package me.iynotuo.judge.captcha.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.CaptchaPurpose;

import java.time.LocalDateTime;

/**
 * One issued CAPTCHA challenge. The answer is represented only by {@link #codeHash}; the clear-text
 * code never reaches this entity or the database.
 */
@Data
public class CaptchaChallenge {

    private String id;

    private CaptchaPurpose purpose;

    private String codeHash;

    private int attempts;

    private LocalDateTime expiresAt;

    private LocalDateTime consumedAt;

    private String clientIp;

    private LocalDateTime createdAt;
}
