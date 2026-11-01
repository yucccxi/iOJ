package me.iynotuo.judge.judge.sandbox;

/**
 * Result of one sandboxed command.
 *
 * <p>It deliberately carries no judging semantics: a submission status is decided by the caller, so the
 * same sandbox serves contestant programs, custom checkers and compilers.</p>
 *
 * @param finished        whether the program ended before the host watchdog killed it
 * @param status          sandbox status: {@code OK}, {@code TO} (timeout), {@code RE} (runtime error),
 *                        {@code SG} (signal), {@code XX} (sandbox failure)
 * @param exitCode        exit code of the executed program, or {@code -1} when unknown
 * @param signal          signal that terminated the program, {@code 0} when none
 * @param timeMs          CPU time consumed, in milliseconds
 * @param memoryKb        peak memory in kilobytes, {@code 0} when the sandbox cannot measure it
 * @param outOfMemory     whether the sandbox reported a memory kill
 * @param stdout          captured standard output, truncated to the configured output limit
 * @param stderr          captured standard error, truncated
 * @param stdoutTruncated whether standard output hit the output limit
 */
public record SandboxResult(boolean finished,
                            String status,
                            int exitCode,
                            int signal,
                            int timeMs,
                            int memoryKb,
                            boolean outOfMemory,
                            String stdout,
                            String stderr,
                            boolean stdoutTruncated) {

    /**
     * Tells whether the program was stopped by either the sandbox or the host watchdog time limit.
     *
     * @return {@code true} when the process was killed for exceeding its time budget
     */
    public boolean timedOut() {
        return !finished || "TO".equals(status);
    }

    /**
     * Tells whether the sandbox itself failed, which is a deployment problem rather than a program error.
     *
     * @return {@code true} when the sandbox reported its own failure
     */
    public boolean internalError() {
        return "XX".equals(status);
    }

    /**
     * Tells whether the sandbox reported a run-time failure, either a signal or a non-zero exit code.
     *
     * <p>Isolate writes {@code RE} for both cases, so contestant programs must treat it as a runtime error
     * before looking at the exit code.</p>
     *
     * @return {@code true} when the sandbox reported {@code SG} or {@code RE}
     */
    public boolean runtimeError() {
        return "SG".equals(status) || "RE".equals(status);
    }

    /**
     * Tells whether the program was terminated by a signal.
     *
     * <p>Distinct from {@link #runtimeError()} on purpose: a custom checker signals "wrong answer" with a
     * non-zero exit code, which isolate also reports as {@code RE}, so only {@code SG} really means the
     * checker crashed.</p>
     *
     * @return {@code true} when the sandbox reported {@code SG}
     */
    public boolean signalled() {
        return "SG".equals(status);
    }
}
