package me.iynotuo.judge.agent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent connection and capacity settings.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ioj.agent")
public class AgentProperties {

    /**
     * Main server base URL, for example {@code https://oj.example.com}.
     */
    private String serverUrl = "http://127.0.0.1:8080";

    /**
     * API key created by an administrator. Override with {@code IOJ_JUDGE_KEY}.
     */
    private String key = "";

    /**
     * Unique node id on this server. Use one id per agent process.
     */
    private String nodeId = "judge-1";

    /**
     * How many submissions this agent judges concurrently.
     */
    private int concurrency = 1;

    /**
     * Idle poll interval.
     */
    private long pollIntervalMs = 1000;

    /**
     * Maximum backoff when the queue stays empty.
     */
    private long maxPollIntervalMs = 5000;

    /**
     * Maximum tasks requested in one claim call. Capped again by the server.
     */
    private int maxClaimBatch = 1;

    /**
     * Initial node heartbeat interval, used until the first node heartbeat response provides the
     * server's offline threshold. The effective interval is one third of that threshold.
     */
    private long nodeHeartbeatIntervalMs = 30_000;

    /**
     * HTTP timeout for one internal API request.
     */
    private int requestTimeoutSeconds = 30;

    /**
     * How many times startup configuration fetching is retried before the agent exits.
     */
    private int configFetchRetries = 5;

    /**
     * Delay between startup configuration fetch retries.
     */
    private long configFetchRetryDelayMs = 2_000;

    /**
     * Timeout for one toolchain probe command.
     */
    private long probeTimeoutMs = 5_000;

    /**
     * Reported agent version; useful when several agents are upgraded at different times.
     */
    private String version = "0.0.1";

    /**
     * Downloaded test data cache. Cached bundles live under
     * {@code directory/problemId/bundle.zip} with a sibling {@code bundle.sha256}.
     */
    private TestcaseCache testcaseCache = new TestcaseCache();

    @Getter
    @Setter
    public static class TestcaseCache {

        /**
         * Whether downloaded bundles are cached on disk.
         */
        private boolean enabled = true;

        /**
         * Cache root, resolved against the agent working directory when relative.
         */
        private String directory = "./cache/testcases";

        /**
         * Maximum total size of cached bundles in bytes. Zero or negative means unlimited. A single
         * bundle larger than this limit is still kept so the task can be judged.
         */
        private long maxBytes = 2L * 1024 * 1024 * 1024;
    }
}
