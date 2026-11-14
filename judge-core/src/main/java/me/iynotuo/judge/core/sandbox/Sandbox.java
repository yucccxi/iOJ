package me.iynotuo.judge.core.sandbox;

/**
 * Executes one command in a restricted environment.
 *
 * <p>A sandbox is mechanism only: it knows how to start a process with limits and how to read its
 * output, but nothing about submissions, answers or verdicts. The isolate implementation enforces
 * limits in the kernel through cgroups and namespaces.</p>
 */
public interface Sandbox extends AutoCloseable {

    /**
     * Short name used in logs and error messages.
     *
     * @return the sandbox name; never {@code null}
     */
    String name();

    /**
     * Runs one command and waits for it to finish.
     *
     * @param command program, working directory, stdin and limits; never {@code null}
     * @return the raw result of the run; never {@code null}
     * @throws Exception when the sandbox cannot start the command at all (for example a broken isolate
     *                   installation); callers report this as a system error
     */
    SandboxResult exec(SandboxCommand command) throws Exception;

    /**
     * Releases sandbox resources. The default implementation does nothing because per-command cleanup
     * already happened.
     */
    @Override
    default void close() {
    }
}
