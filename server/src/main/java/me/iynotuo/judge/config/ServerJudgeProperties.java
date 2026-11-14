package me.iynotuo.judge.config;

import lombok.Getter;
import lombok.Setter;
import me.iynotuo.judge.protocol.Language;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side judge dispatch configuration.
 *
 * <p>The server only dispatches work to remote agents and persists their results. Execution
 * settings such as the isolate binary, sandbox limits, checker commands and compile timeouts are
 * configured on the agent through its own {@code JudgeProperties}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ioj.judge")
public class ServerJudgeProperties {

    /**
     * Settings for remote task dispatch and lease management.
     */
    private Dispatch dispatch = new Dispatch();

    /**
     * Language command snapshots sent to agents in each claim and shown by the public meta endpoint.
     */
    private Map<Language, LanguageConfig> languages = new EnumMap<>(Language.class);

    /**
     * Settings the server sends to an agent so it can verify and enable custom checkers.
     */
    private Checker checker = new Checker();

    /**
     * Configuration for a single language.
     */
    @Getter
    @Setter
    public static class LanguageConfig {

        private String displayName;

        private String sourceFile;

        /**
         * Compile command; empty for interpreted languages that need no compilation.
         */
        private String compileCommand;

        /**
         * Run command; may contain the placeholders supported by the agent.
         */
        private String runCommand;

        /**
         * Wrapper for the run command; {@code {command}} is replaced by the run command.
         */
        private String runWrapper = "exec {command}";

        /**
         * Time multiplier that compensates for fixed overhead such as JVM startup.
         */
        private double timeMultiplier = 1.0;

        /**
         * Number of processes/threads allowed in the Isolate sandbox while running this language.
         */
        private Integer isolateProcesses;

        /**
         * Extra memory (MB) reserved for the runtime itself, on top of the problem limit.
         */
        private Integer isolateMemoryExtraMb;

        /**
         * Whether the source file must be copied into each run directory even when a compile command
         * is configured. This is needed for interpreted languages such as Python.
         */
        private boolean copySourceToRun;

        /**
         * Commands the agent runs at startup to verify this language's toolchain.
         */
        private List<String> probeCommands = new ArrayList<>();
    }

    /**
     * Remote dispatch settings: how long an agent owns a task and when the server reaps it.
     */
    @Getter
    @Setter
    public static class Dispatch {

        /**
         * Lease duration granted to a judge agent. The agent renews it with a heartbeat.
         */
        private int leaseSeconds = 300;

        /**
         * How many times a task may be claimed before it is failed instead of requeued.
         */
        private int maxAttempts = 3;

        /**
         * How often the server looks for expired leases, in milliseconds.
         */
        private long reaperIntervalMs = 30_000;

        /**
         * A node whose last heartbeat is older than this is shown as offline.
         */
        private long nodeOfflineSeconds = 90;

        /**
         * Upper bound on tasks returned by one claim request.
         */
        private int maxClaimBatch = 10;

        /**
         * When the combined UTF-8 size of a task's test data exceeds this many bytes, the claim
         * response omits the inline test cases and points at the ZIP bundle endpoint instead.
         */
        private int inlineMaxBytes = 1_048_576;
    }

    /**
     * Custom checker settings that the server forwards in the agent bootstrap config.
     */
    @Getter
    @Setter
    public static class Checker {

        /**
         * Whether custom checkers are allowed at all.
         */
        private boolean enabled = true;

        /**
         * Commands the agent runs at startup to verify the checker compiler.
         */
        private List<String> probeCommands = new ArrayList<>();
    }
}
