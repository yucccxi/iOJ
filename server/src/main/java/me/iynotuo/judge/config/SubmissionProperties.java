package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Submission configuration.
 */
@ConfigurationProperties(prefix = "ioj.submission")
public class SubmissionProperties {

    /**
     * Maximum source length per submission, in characters
     * ({@code ioj.submission.max-code-length}, overridable with
     * {@code IOJ_SUBMISSION_MAX_CODE_LENGTH}). Defaults to about 64K characters.
     *
     * <p>The request DTO has no {@code @Size} annotation on purpose, so this configured value
     * is what actually decides whether a submission is rejected with {@code CODE_TOO_LONG}.</p>
     */
    private int maxCodeLength = 64 * 1024;

    public int getMaxCodeLength() {
        return maxCodeLength;
    }

    public void setMaxCodeLength(int maxCodeLength) {
        this.maxCodeLength = maxCodeLength;
    }
}
