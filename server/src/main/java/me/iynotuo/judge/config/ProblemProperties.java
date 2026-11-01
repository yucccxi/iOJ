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
     * Maximum size of a single test case input or output, in UTF-8 bytes
     * ({@code ioj.problem.max-testcase-bytes}, overridable with
     * {@code IOJ_PROBLEM_MAX_TESTCASE_BYTES}).
     *
     * <p>This is the single source of truth for the limit: the request DTO has no
     * {@code @Size} annotation, so the configured value is the one that is enforced.</p>
     */
    private int maxTestcaseBytes = 4 * 1024 * 1024;

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
