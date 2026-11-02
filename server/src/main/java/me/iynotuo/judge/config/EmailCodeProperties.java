package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email verification code configuration ({@code ioj.email-code.*}).
 *
 * <p>All limits live here on purpose: an operator can tighten or disable them per deployment
 * without touching code, and they are independent from the SMTP settings in {@code spring.mail}.</p>
 */
@ConfigurationProperties(prefix = "ioj.email-code")
public class EmailCodeProperties {

    /**
     * Whether registration requires an email code. When false the send endpoint reports that the
     * feature is off and {@code POST /auth/register} does not ask for a code. Defaults to true;
     * the test profile turns it off because most integration tests only need an account.
     */
    private boolean enabled = true;

    /**
     * How long an issued code stays valid, in minutes ({@code ioj.email-code.ttl-minutes},
     * overridable with {@code IOJ_EMAIL_CODE_TTL_MINUTES}).
     */
    private int ttlMinutes = 10;

    /**
     * Minimum gap between two codes for the same address, in seconds.
     */
    private int resendIntervalSeconds = 60;

    /**
     * Maximum number of codes that may be requested for the same address within one hour.
     */
    private int maxPerHour = 5;

    /**
     * How many wrong submissions a single code tolerates. Reaching this limit invalidates the
     * code immediately, so a later correct guess is still rejected.
     */
    private int maxAttempts = 5;

    /**
     * Sender address of the verification mail. Most SMTP servers reject a mismatched From, so
     * override it (with {@code IOJ_MAIL_FROM}) to an address the SMTP account may send as.
     */
    private String from = "no-reply@localhost";

    /**
     * Subject line of the registration mail.
     */
    private String subject = "iOJ 邮箱验证码";

    /**
     * Subject line of the password reset mail. A distinct subject makes the two mails easy to tell
     * apart in an inbox full of automated messages.
     */
    private String subjectReset = "iOJ 找回密码验证码";

    /**
     * How long consumed or expired rows are kept before the cleanup task removes them, in hours.
     */
    private int cleanupRetentionHours = 24;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(int ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public int getResendIntervalSeconds() {
        return resendIntervalSeconds;
    }

    public void setResendIntervalSeconds(int resendIntervalSeconds) {
        this.resendIntervalSeconds = resendIntervalSeconds;
    }

    public int getMaxPerHour() {
        return maxPerHour;
    }

    public void setMaxPerHour(int maxPerHour) {
        this.maxPerHour = maxPerHour;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubjectReset() {
        return subjectReset;
    }

    public void setSubjectReset(String subjectReset) {
        this.subjectReset = subjectReset;
    }

    public int getCleanupRetentionHours() {
        return cleanupRetentionHours;
    }

    public void setCleanupRetentionHours(int cleanupRetentionHours) {
        this.cleanupRetentionHours = cleanupRetentionHours;
    }
}
