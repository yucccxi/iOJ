package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 提交相关配置。
 */
@ConfigurationProperties(prefix = "ioj.submission")
public class SubmissionProperties {

    /**
     * 单次提交代码的最大字符数（约 64KB）。
     */
    private int maxCodeLength = 64 * 1024;

    public int getMaxCodeLength() {
        return maxCodeLength;
    }

    public void setMaxCodeLength(int maxCodeLength) {
        this.maxCodeLength = maxCodeLength;
    }
}
