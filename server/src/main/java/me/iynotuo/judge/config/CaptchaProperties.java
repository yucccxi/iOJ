package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Image CAPTCHA settings. Challenge answers are never stored in configuration; only lifetime,
 * attempt limit, image length and per-IP issuance limits live here.
 */
@ConfigurationProperties(prefix = "ioj.captcha")
public class CaptchaProperties {

    /**
     * Whether the captcha endpoint is enabled and auth flows require a challenge.
     */
    private boolean enabled = true;

    /**
     * How long a challenge stays valid.
     */
    private int ttlSeconds = 120;

    /**
     * Wrong answers allowed for one challenge before it is invalidated.
     */
    private int maxAttempts = 3;

    /**
     * Number of characters rendered in one image.
     */
    private int codeLength = 5;

    /**
     * Per-IP issuance limit in a one-minute window.
     */
    private int issuePerMinutePerIp = 60;

    /**
     * Per-IP issuance limit in a one-hour window.
     */
    private int issuePerHourPerIp = 600;

    /**
     * Expired challenges are kept this long for diagnostics and rate-limit windows.
     */
    private int cleanupRetentionHours = 24;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public int getIssuePerMinutePerIp() {
        return issuePerMinutePerIp;
    }

    public void setIssuePerMinutePerIp(int issuePerMinutePerIp) {
        this.issuePerMinutePerIp = issuePerMinutePerIp;
    }

    public int getIssuePerHourPerIp() {
        return issuePerHourPerIp;
    }

    public void setIssuePerHourPerIp(int issuePerHourPerIp) {
        this.issuePerHourPerIp = issuePerHourPerIp;
    }

    public int getCleanupRetentionHours() {
        return cleanupRetentionHours;
    }

    public void setCleanupRetentionHours(int cleanupRetentionHours) {
        this.cleanupRetentionHours = cleanupRetentionHours;
    }
}
