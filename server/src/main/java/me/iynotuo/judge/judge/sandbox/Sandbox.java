package me.iynotuo.judge.judge.sandbox;

/**
 * Executes one command in a restricted environment.
 *
 * <p>A sandbox is mechanism only: it knows how to start a process with limits and how to read its
 * output, but nothing about submissions, answers or verdicts. The isolate implementation enforces
 * limits in the kernel through cgroups and namespaces; the local implementation uses a temporary
 * directory plus {@code ulimit} and exists only for machines without a sandbox.</p>
 */
public interface Sandbox extends AutoCloseable {

    /**
     * Short name used in logs and error messages.
     *
     * @return {@code "isolate"} or {@code "local"}; never {@code null}
     */
    String name();

    /**
     * Tells whether this sandbox is safe enough to run code uploaded by third parties.
     *
     * @return {@code true} for the isolate sandbox, {@code false} for the local fallback, which cannot
     *         stop a hostile program from touching the host
     */
    boolean supportsAuthorCode();

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
