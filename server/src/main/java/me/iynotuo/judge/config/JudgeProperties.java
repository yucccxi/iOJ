package me.iynotuo.judge.config;

import lombok.Getter;
import lombok.Setter;
import me.iynotuo.judge.common.enums.Language;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Judge configuration. Language commands support these placeholders:
 * <ul>
 * <li>{@code {command}}: the expanded run command, used only in the run wrapper;</li>
 * <li>{@code {memoryMb}} / {@code {memoryKb}}: the problem memory limit;</li>
 * <li>{@code {timeMs}}: the time limit for this problem (already multiplied by the language factor).</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ioj.judge")
public class JudgeProperties {

    /**
     * Whether the judge worker is enabled. When disabled, submissions stay PENDING; intended for API-only debugging.
     */
    private boolean enabled = true;

    /**
     * Judge engine implementation: {@code isolate} (recommended), {@code local} (in-process fallback)
     * or {@code docker} (not implemented yet). The value must match the condition on the engine bean.
     */
    private String engine = "isolate";

    /**
     * Number of submissions judged concurrently.
     */
    private int concurrency = 1;

    /**
     * Polling interval for pending submissions, in milliseconds.
     */
    private long pollIntervalMs = 1000;

    /**
     * Maximum output size per test case; a larger output is reported as wrong answer or output limit exceeded.
     */
    private int maxOutputBytes = 16 * 1024 * 1024;

    /**
     * Maximum number of bytes kept from compile output (stdout and stderr).
     */
    private int maxCompileOutputBytes = 64 * 1024;

    /**
     * Compile timeout in milliseconds.
     */
    private long compileTimeoutMs = 30_000;

    private Map<Language, LanguageConfig> languages = new EnumMap<>(Language.class);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getMaxCompileOutputBytes() {
        return maxCompileOutputBytes;
    }

    public void setMaxCompileOutputBytes(int maxCompileOutputBytes) {
        this.maxCompileOutputBytes = maxCompileOutputBytes;
    }

    public long getCompileTimeoutMs() {
        return compileTimeoutMs;
    }

    public void setCompileTimeoutMs(long compileTimeoutMs) {
        this.compileTimeoutMs = compileTimeoutMs;
    }

    public Map<Language, LanguageConfig> getLanguages() {
        return languages;
    }

    public void setLanguages(Map<Language, LanguageConfig> languages) {
        this.languages = languages;
    }

    /**
     * Settings for the Isolate sandbox engine; ignored when another engine is selected.
     */
    private Isolate isolate = new Isolate();

    /**
     * Configuration for a single language.
     */
    @Getter
    @Setter
    public static class LanguageConfig {

        private String displayName;

        private String sourceFile;

        /**
         * Compile command; leave empty for interpreted languages that need no compilation.
         */
        private String compileCommand;

        /**
         * Run command; may contain {memoryMb} and {memoryKb} placeholders.
         */
        private String runCommand;

        /**
         * Wrapper script for the run command; {command} is replaced by the run command. Defaults to a plain exec.
         */
        private String runWrapper = "exec {command}";

        /**
         * Time multiplier that compensates for fixed overhead such as JVM startup.
         */
        private double timeMultiplier = 1.0;

        /**
         * Number of processes/threads allowed in the Isolate sandbox while running this language.
         * Leave empty to use 1 (C/C++/Python) or set 32+ for the JVM.
         */
        private Integer isolateProcesses;

        /**
         * Extra memory (MB) reserved for the runtime itself, on top of the problem limit.
         * The JVM needs a few hundred MB for metaspace, code cache and stacks.
         */
        private Integer isolateMemoryExtraMb;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }

        public String getCompileCommand() {
            return compileCommand;
        }

        public void setCompileCommand(String compileCommand) {
            this.compileCommand = compileCommand;
        }

        public String getRunCommand() {
            return runCommand;
        }

        public void setRunCommand(String runCommand) {
            this.runCommand = runCommand;
        }

        public String getRunWrapper() {
            return runWrapper;
        }

        public void setRunWrapper(String runWrapper) {
            this.runWrapper = runWrapper;
        }

        public double getTimeMultiplier() {
            return timeMultiplier;
        }

        public void setTimeMultiplier(double timeMultiplier) {
            this.timeMultiplier = timeMultiplier;
        }
    }

    /**
     * Settings for the Isolate sandbox engine.
     */
    @Getter
    @Setter
    public static class Isolate {

        /**
         * Absolute path of the isolate binary. The service user must be allowed to execute it
         * (usually by being a member of the {@code isolate} group).
         */
        private String binary = "/usr/bin/isolate";

        /**
         * First box id used by this instance. Box ids must be unique on one machine and must not
         * collide with boxes created by another user (box 0 is often left over from root tests).
         */
        private int boxIdStart = 1;

        /**
         * Number of box ids reserved for this instance; should be at least the judge concurrency.
         */
        private int boxIdCount = 8;

        /**
         * Whether to fail fast at startup when the isolate binary cannot be executed.
         */
        private boolean checkOnStartup = true;

        /**
         * Processes/threads allowed while compiling (compilers spawn several helper processes).
         */
        private int compileProcesses = 64;

        /**
         * Extra host directories to bind read-only into the sandbox at the same path.
         * The JVM needs the directory that contains {@code security/java.security}
         * (on Debian: /etc/java-21-openjdk). Leave empty if your runtime does not need it.
         */
        private List<String> extraDirs = new ArrayList<>();

        /**
         * Resolve the first token of compile/run commands to its real host path before invoking
         * Isolate. This avoids passing symlinks that point into /etc/alternatives, which is not
         * mounted in the sandbox by default.
         */
        private boolean resolveExecutables = true;

        /**
         * CPU time limit for compilation, in seconds.
         */
        private double compileTimeSeconds = 10.0;

        /**
         * Wall-clock limit for compilation, in seconds.
         */
        private double compileWallTimeSeconds = 30.0;

        /**
         * Memory limit for compilation, in KB.
         */
        private int compileCgMemKb = 512 * 1024;

        /**
         * Maximum file size inside the compile sandbox, in KB.
         */
        private int compileFsizeKb = 16 * 1024;

        /**
         * Extra wall-clock seconds granted on top of the CPU limit for a normal run.
         */
        private int wallTimeExtraSeconds = 3;

        /**
         * Timeout for an isolate init/cleanup invocation, in milliseconds.
         */
        private long setupTimeoutMs = 10_000;
    }
}
