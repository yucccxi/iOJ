package me.iynotuo.judge.judge.sandbox;

import java.nio.file.Path;
import java.util.List;

/**
 * One command to execute inside a {@link Sandbox}.
 *
 * <p>All paths are host paths. A sandbox translates them into its own namespace: the isolate
 * implementation mounts {@code directory} at {@code /box} and rewrites paths below it, while the local
 * implementation uses them as they are.</p>
 *
 * @param directory    host directory used as the working directory of the command
 * @param argv         program and arguments; a first token that is a plain file name is resolved on
 *                     {@code PATH} or against {@code directory}
 * @param stdin        host path of the file fed to standard input, or {@code null} for empty input
 * @param limits       resource limits of this command
 * @param processes    maximum number of processes/threads; values below 1 are clamped to 1
 * @param shellWrapper optional bash wrapper applied by shell based sandboxes (local), with
 *                     {@code {command}} replaced by the joined argv. In-kernel sandboxes ignore it
 *                     because they enforce limits themselves
 */
public record SandboxCommand(Path directory,
                             List<String> argv,
                             Path stdin,
                             Limits limits,
                             int processes,
                             String shellWrapper) {

    /**
     * Creates a command without a shell wrapper.
     *
     * @param directory host working directory
     * @param argv      program and arguments
     * @param stdin     host path of the stdin file, or {@code null}
     * @param limits    resource limits
     * @param processes maximum number of processes/threads
     */
    public SandboxCommand(Path directory, List<String> argv, Path stdin, Limits limits, int processes) {
        this(directory, argv, stdin, limits, processes, null);
    }
}
