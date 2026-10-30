package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Submission configuration.
 */
@ConfigurationProperties(prefix = "ioj.submission")
public class SubmissionProperties {

    /**
     * Maximum source length per submission (about 64 KB).
     */
    private int maxCodeLength = 64 * 1024;

    public int getMaxCodeLength() {
        return maxCodeLength;
    }

    public void setMaxCodeLength(int maxCodeLength) {
        this.maxCodeLength = maxCodeLength;
    }
}
