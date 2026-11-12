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
     * Task lease requested from the server.
     */
    private int leaseSeconds = 300;

    /**
     * How often the agent renews its node and task leases.
     */
    private long heartbeatIntervalMs = 100_000;

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
}
