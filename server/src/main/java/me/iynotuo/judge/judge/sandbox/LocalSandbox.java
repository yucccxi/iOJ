package me.iynotuo.judge.judge.sandbox;

import me.iynotuo.judge.config.JudgeProperties;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Local process sandbox: runs the command on the host with a temporary working directory, bash
 * {@code ulimit} and a wall-clock timeout.
 *
 * <p>It exists only for machines without isolate (development, CI) and is <strong>not</strong> a security
 * boundary: a hostile program can still exhaust the CPU, fork bomb or reach the network, and it runs with
 * the judge user's privileges. Because of that {@link #supportsAuthorCode()} is {@code false}, so custom
 * checkers are never executed here.</p>
 */
public class LocalSandbox implements Sandbox {

    private static final int STDERR_LIMIT = 8 * 1024;
    private static final String COMMAND_PLACEHOLDER = "{command}";

    private final JudgeProperties properties;

    /**
     * Creates the fallback sandbox.
     *
     * @param properties judge configuration; supplies the output size cap. Never {@code null}
     */
    public LocalSandbox(JudgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public boolean supportsAuthorCode() {
        return false;
    }

    @Override
    public SandboxResult exec(SandboxCommand command) throws Exception {
        String script = script(command);
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", script).directory(command.directory().toFile());
        if (command.stdin() != null) {
            builder.redirectInput(command.stdin().toFile());
        }
        long start = System.nanoTime();
        Process process = builder.start();
        process.getOutputStream().close();

        StreamCollector stdout = new StreamCollector(process.getInputStream(), properties.getMaxOutputBytes());
        StreamCollector stderr = new StreamCollector(process.getErrorStream(), STDERR_LIMIT);
        Thread stdoutThread = daemon(stdout, "judge-stdout");
        Thread stderrThread = daemon(stderr, "judge-stderr");

        boolean finished = process.waitFor(Math.max(1, command.limits().timeMs()), TimeUnit.MILLISECONDS);
        int timeMs = (int) Math.min((System.nanoTime() - start) / 1_000_000L, Integer.MAX_VALUE);
        int exitCode = -1;
        if (finished) {
            exitCode = process.exitValue();
        } else {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
        stdoutThread.join(1_000);
        stderrThread.join(1_000);

        String status = !finished ? "TO" : (exitCode == 0 ? "OK" : "RE");
        return new SandboxResult(finished, status, exitCode, 0, timeMs, 0, false,
                stdout.text(), stderr.text(), stdout.truncated());
    }

    /**
     * Builds the bash script for one command.
     *
     * @param command command to run
     * @return the script, with {@code {command}} replaced when a shell wrapper is configured
     */
    private String script(SandboxCommand command) {
        String joined = String.join(" ", shellArgv(command.argv(), command.directory()));
        if (!StringUtils.hasText(command.shellWrapper())) {
            return joined;
        }
        return command.shellWrapper().replace(COMMAND_PLACEHOLDER, joined);
    }

    /**
     * Makes a bare executable name inside the working directory runnable by bash.
     *
     * @param argv host argv as configured
     * @param dir  host working directory
     * @return the argv with {@code ./} prefixed when the first token is a file in the directory
     */
    private List<String> shellArgv(List<String> argv, Path dir) {
        if (argv.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(argv);
        String executable = result.getFirst();
        boolean plainName = !executable.contains("/");
        if (plainName && Files.isRegularFile(dir.resolve(executable))) {
            result.set(0, "./" + executable);
        }
        return result;
    }

    /**
     * Starts a daemon thread that drains one output stream of a child process.
     *
     * @param runnable the draining task, normally a {@link StreamCollector}
     * @param name     thread name, used in thread dumps and logs
     * @return the already started thread, so the caller can join it and then read the collected text
     */
    private Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
