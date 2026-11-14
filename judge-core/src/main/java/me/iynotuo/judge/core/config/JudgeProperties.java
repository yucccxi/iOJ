package me.iynotuo.judge.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Judge execution configuration used by judge-core and the agent.
 *
 * <p>Language commands support these placeholders:
 * <ul>
 * <li>{@code {command}}: the expanded run command, used only in the run wrapper;</li>
 * <li>{@code {memoryMb}} / {@code {memoryKb}}: the problem memory limit;</li>
 * <li>{@code {timeMs}}: the time limit for this problem (already multiplied by the language factor).</li>
 * </ul>
 *
 * <p>Server-side dispatch settings are not part of this class; the server keeps them in
 * {@code me.iynotuo.judge.config.ServerJudgeProperties}. This class is registered by the agent
 * application and used internally by judge-core.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ioj.judge")
public class JudgeProperties {

    /**
     * Maximum output size per test case; a larger output is reported as wrong answer or output limit exceeded.
     */
    private int maxOutputBytes = 16 * 1024 * 1024;

    /**
     * Maximum number of bytes kept from compiler stderr and from each run's stderr. The local engine
     * also truncates the compile message to this many characters; isolate compile stdout is capped by
     * {@link #maxOutputBytes} instead.
     */
    private int maxCompileOutputBytes = 64 * 1024;

    /**
     * Compile timeout for the local engine, in milliseconds. The isolate engine uses
     * {@code ioj.judge.isolate.compile-time-seconds} instead.
     */
    private long compileTimeoutMs = 30_000;

    /**
     * Settings for the optional per-problem custom checker (special judge).
     */
    private Checker checker = new Checker();

    /**
     * Settings for the Isolate sandbox engine. The agent bootstrap probes {@code binary} at every
     * startup regardless of the selected engine, so it must exist even when {@code engine=local}.
     */
    private Isolate isolate = new Isolate();

    /**
     * Configuration for a single language.
     */
    @Getter
    @Setter
    public static class LanguageConfig {

        private String sourceFile;

        /**
         * Compile command; leave empty for interpreted languages that need no compilation.
         */
        private String compileCommand;

        /**
         * Run command; may contain {memoryMb}, {memoryKb} and {timeMs} placeholders.
         */
        private String runCommand;

        /**
         * Wrapper script for the run command; {command} is replaced by the run command. Defaults to a plain exec.
         */
        private String runWrapper = "exec {command}";

        /**
         * Whether the source file must be copied into each run directory even when a compile command is
         * configured. This is needed for interpreted languages such as Python, where the compile step is
         * only a syntax check and the runtime still executes the source file.
         */
        private boolean copySourceToRun;

        /**
         * Commands the judge agent runs at startup to verify this language's toolchain. Each command
         * must exit with status 0.
         */
        private List<String> probeCommands = new ArrayList<>();

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
         * Number of box ids reserved for this instance. Should be at least the number of concurrently
         * judged submissions, which is {@code ioj.agent.concurrency} on an agent.
         */
        private int boxIdCount = 8;

        /**
         * Whether the IsolateSandbox constructor fails fast when the binary is not executable. The
         * agent bootstrap probes isolate independently, regardless of this flag.
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

    /**
     * Settings for a problem's custom checker (special judge).
     *
     * <p>The checker is C++ source code uploaded by the problem author. It is compiled with
     * {@code compileCommand} and then run once per test case in its own sandbox with three files
     * available in the working directory: {@code input.txt}, {@code output.txt} (the contestant
     * output) and {@code answer.txt} (the official answer). The exit code decides the verdict:
     * {@code 0} accept, {@code 1} wrong answer, {@code 2} presentation error, anything else is
     * reported as {@code CHECKER_ERROR}. Text printed on stdout is stored as the case message.</p>
     *
     * <p>The limits below are deliberately separate from the problem limits: a checker usually needs
     * more memory and a different time budget than the contestant program, and a checker that runs
     * out of limits must never be reported as a contestant failure.</p>
     */
    @Getter
    @Setter
    public static class Checker {

        /**
         * Whether custom checkers are allowed at all. When disabled, problems that carry a checker are
         * judged with {@code CHECKER_ERROR} instead of running author-supplied code.
         */
        private boolean enabled = true;

        /**
         * CPU time limit for one checker run, in milliseconds.
         */
        private int timeLimitMs = 2_000;

        /**
         * Memory limit for one checker run, in megabytes.
         */
        private int memoryLimitMb = 512;

        /**
         * CPU time limit for compiling the checker inside the isolate sandbox, in milliseconds. The
         * wall-clock limit is this value plus a few seconds.
         */
        private long compileTimeoutMs = 30_000;

        /**
         * Memory limit for compiling the checker, in megabytes.
         */
        private int compileMemoryMb = 512;

        /**
         * File name the checker source is written to before compilation.
         */
        private String sourceFile = "checker.cpp";

        /**
         * Compile command; it must produce an executable named {@code checker} in the working directory.
         */
        private String compileCommand = "g++ -O2 -std=c++17 -pipe -o checker checker.cpp";

        /**
         * Maximum number of characters kept from the checker output, matching the
         * {@code VARCHAR(1024)} width of {@code submission_cases.message}.
         */
        private int messageLimitBytes = 1_024;

        /**
         * Commands the judge agent runs at startup to verify the checker compiler.
         */
        private List<String> probeCommands = new ArrayList<>();
    }
}
