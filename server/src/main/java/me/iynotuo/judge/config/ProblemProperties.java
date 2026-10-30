package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Problem configuration.
 */
@ConfigurationProperties(prefix = "ioj.problem")
public class ProblemProperties {

    /**
     * Whether problems uploaded by regular users are published immediately. When false they enter PENDING_REVIEW until an administrator approves them.
     * Recommended true for a trusted club and false when registration is open to the public.
     */
    private boolean autoPublish = true;

    /**
     * Maximum number of test cases per problem.
     */
    private int maxTestcases = 100;

    /**
     * Maximum size of a single test case input or output, in bytes.
     */
    private int maxTestcaseBytes = 1024 * 1024;

    public boolean isAutoPublish() {
        return autoPublish;
    }

    public void setAutoPublish(boolean autoPublish) {
        this.autoPublish = autoPublish;
    }

    public int getMaxTestcases() {
        return maxTestcases;
    }

    public void setMaxTestcases(int maxTestcases) {
        this.maxTestcases = maxTestcases;
    }

    public int getMaxTestcaseBytes() {
        return maxTestcaseBytes;
    }

    public void setMaxTestcaseBytes(int maxTestcaseBytes) {
        this.maxTestcaseBytes = maxTestcaseBytes;
    }
}
