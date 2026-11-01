package me.iynotuo.judge.judge.sandbox;

import me.iynotuo.judge.config.JudgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Isolate based sandbox.
 *
 * <p>Every command runs in its own box: the box id is taken from a pool, {@code isolate --init} runs,
 * the command executes with cgroup limits, and {@code isolate --cleanup} releases the box. The working
 * directory of the command is bind-mounted at {@code /box}, so the judge process never needs write
 * access to the isolate box root.</p>
 *
 * <p>Sandbox managed files inside the working directory: {@code stdout.txt} (standard output),
 * {@code stderr.txt} (standard error) and {@code empty.txt} (empty stdin). Callers may safely keep their
 * own {@code input.txt}, {@code output.txt} and {@code answer.txt} next to them.</p>
 */
public class IsolateSandbox implements Sandbox {

    private static final Logger log = LoggerFactory.getLogger(IsolateSandbox.class);

    private static final int ISOLATE_OUTPUT_LIMIT = 64 * 1024;
    private static final String BOX_DIR = "/box";
    private static final String STDOUT_FILE = "stdout.txt";
    private static final String STDERR_FILE = "stderr.txt";
    private static final String EMPTY_FILE = "empty.txt";

    private final JudgeProperties properties;
    private final Path binary;
    private final BlockingQueue<Integer> boxIds;

    /**
     * Creates the sandbox and validates the isolate prerequisites.
     *
     * @param properties judge configuration; {@code properties.getIsolate()} supplies the binary path,
     *                   the box id pool and the setup timeouts. Never {@code null}
     * @throws IllegalStateException when startup verification is enabled and the isolate binary is not
     *                               executable, so a misconfigured deployment fails fast instead of
     *                               failing every submission
     */
    public IsolateSandbox(JudgeProperties properties) {
        this.properties = properties;
        JudgeProperties.Isolate isolate = properties.getIsolate();
        this.binary = Path.of(isolate.getBinary());
        if (isolate.isCheckOnStartup() && !Files.isExecutable(binary)) {
            throw new IllegalStateException("isolate binary is not executable: " + binary
                    + ". Add the service user to the 'isolate' group and log in again: "
                    + "sudo usermod -aG isolate $(whoami)");
        }
        int start = Math.max(0, isolate.getBoxIdStart());
        int count = Math.max(1, isolate.getBoxIdCount());
        this.boxIds = new ArrayBlockingQueue<>(count);
        for (int i = 0; i < count; i++) {
            boxIds.add(start + i);
        }
    }

    @Override
    public String name() {
        return "isolate";
    }

    @Override
    public boolean supportsAuthorCode() {
        return true;
    }

    @Override
    public SandboxResult exec(SandboxCommand command) throws Exception {
        int boxId;
        try {
            boxId = boxIds.take();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for an isolate box", ex);
        }
        try {
            initBox(boxId);
            return runInBox(boxId, command);
        } finally {
            cleanupBox(boxId);
            boxIds.offer(boxId);
        }
    }

    /**
     * Runs one command inside an already initialized box and reads its output back.
     *
     * @param boxId   id of the initialized box; owned by the caller
     * @param command command to execute
     * @return the raw result, including the captured output files
     * @throws Exception when the isolate process cannot be started
     */
    private SandboxResult runInBox(int boxId, SandboxCommand command) throws Exception {
        Path dir = command.directory();
        Path metaFile = dir.getParent().resolve(dir.getFileName() + ".meta");
        Path stdoutFile = dir.resolve(STDOUT_FILE);
        Path stderrFile = dir.resolve(STDERR_FILE);
        // Isolate silently drops output when the target file does not exist, so both files must exist first.
        SandboxFiles.writeFile(stdoutFile, "", "rw-rw-rw-");
        SandboxFiles.writeFile(stderrFile, "", "rw-rw-rw-");
        SandboxFiles.writeFile(dir.resolve(EMPTY_FILE), "", "rw-rw-rw-");

        String stdin = command.stdin() == null
                ? BOX_DIR + "/" + EMPTY_FILE
                : toSandboxPath(command.stdin(), dir);
        List<String> argv = boxArgv(command.argv(), dir);

        List<String> isolateCommand = isolateCommand(boxId, dir, stdin,
                BOX_DIR + "/" + STDOUT_FILE, BOX_DIR + "/" + STDERR_FILE, metaFile,
                command.processes(), command.limits(), argv);
        ProcResult process = execProcess(isolateCommand, (command.limits().wallSeconds() + 10L) * 1000L);

        IsolateMeta meta = IsolateMeta.parse(metaFile);
        String stdout = SandboxFiles.readText(stdoutFile, properties.getMaxOutputBytes());
        String stderr = SandboxFiles.readText(stderrFile, properties.getMaxCompileOutputBytes());
        boolean truncated = SandboxFiles.size(stdoutFile) >= properties.getMaxOutputBytes();
        if (!process.finished() && StringUtils.hasText(process.output())) {
            log.debug("isolate watchdog fired for box {}: {}", boxId, process.output());
        }
        return new SandboxResult(process.finished(), meta.status(), meta.exitCode(), meta.signal(),
                meta.timeMs(), meta.memoryKb(), meta.outOfMemory(), stdout, stderr, truncated);
    }

    /**
     * Assembles a complete {@code isolate --run} argv.
     *
     * <p>Adds the cgroup resource limits, a minimal environment ({@code PATH}, {@code TMPDIR},
     * {@code LANG}), the writable bind mount for {@code /box}, one read-only bind mount per
     * {@code ioj.judge.isolate.extra-dirs} entry, stdio redirection and the meta file.</p>
     *
     * @param boxId       id of the initialized box to run in
     * @param dir         host directory exposed writable as {@code /box}
     * @param stdin       sandbox path of the stdin file
     * @param stdout      sandbox path of the stdout file
     * @param stderr      sandbox path of the stderr file
     * @param metaFile    host path of the meta file
     * @param processes   max processes/threads inside the box; values below 1 are clamped to 1
     * @param limits      CPU, wall clock, memory and file size limits
     * @param program     program and arguments inside the box; must be non-empty
     * @return a new mutable argv list whose first element is the isolate binary
     */
    List<String> isolateCommand(int boxId, Path dir, String stdin, String stdout, String stderr,
                                        Path metaFile, int processes, Limits limits, List<String> program) {
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.add("--cg");
        command.add("--box-id=" + boxId);
        command.add("--processes=" + Math.max(1, processes));
        command.add("--time=" + format(limits.timeMs() / 1000.0));
        command.add("--wall-time=" + format(limits.wallSeconds()));
        command.add("--cg-mem=" + Math.max(1024, limits.cgMemKb()));
        command.add("--fsize=" + Math.max(64, limits.fsizeKb()));
        command.add("--env=PATH=/usr/bin:/bin");
        command.add("--env=TMPDIR=" + BOX_DIR);
        command.add("--env=LANG=C.UTF-8");
        command.add("--dir=" + BOX_DIR + "=" + dir.toAbsolutePath() + ":rw");
        for (String extraDir : properties.getIsolate().getExtraDirs()) {
            if (StringUtils.hasText(extraDir)) {
                command.add("--dir=" + extraDir + "=" + extraDir);
            }
        }
        command.add("--chdir=" + BOX_DIR);
        command.add("--stdin=" + stdin);
        command.add("--stdout=" + stdout);
        command.add("--stderr=" + stderr);
        command.add("--meta=" + metaFile.toAbsolutePath());
        command.add("--run");
        command.add("--");
        command.addAll(program);
        return command;
    }

    /**
     * Initializes (and implicitly cleans) the given box before a run.
     *
     * @param boxId box id owned by this sandbox; must not be used by another thread at the same time
     * @throws IOException           when the isolate process cannot be started
     * @throws InterruptedException  when the calling thread is interrupted while waiting for isolate
     * @throws IllegalStateException when isolate exits non-zero or prints no existing box root, typically
     *                               because the box belongs to another user or the cgroup service is down
     */
    private void initBox(int boxId) throws IOException, InterruptedException {
        JudgeProperties.Isolate isolate = properties.getIsolate();
        ProcResult result = execProcess(List.of(binary.toString(), "--cg", "--box-id=" + boxId, "--init"),
                isolate.getSetupTimeoutMs());
        String boxRoot = result.stdout().trim();
        if (!result.finished() || result.exitCode() != 0 || boxRoot.isEmpty()
                || !Files.isDirectory(Path.of(boxRoot))) {
            throw new IllegalStateException("isolate --init failed for box " + boxId + ": " + result.output());
        }
    }

    /**
     * Cleans the box after a run, releasing its cgroup and files.
     *
     * <p>Failures are logged but never propagated: cleanup runs from a {@code finally} block and must not
     * mask the judging result. Cleaning an already clean box is harmless.</p>
     *
     * @param boxId id of the box to clean
     */
    private void cleanupBox(int boxId) {
        try {
            ProcResult result = execProcess(
                    List.of(binary.toString(), "--cg", "--box-id=" + boxId, "--cleanup"),
                    properties.getIsolate().getSetupTimeoutMs());
            if (!result.finished() || result.exitCode() != 0) {
                log.warn("isolate --cleanup failed for box {}: {}", boxId, result.output());
            }
        } catch (Exception ex) {
            log.warn("isolate --cleanup failed for box {}", boxId, ex);
        }
    }

    /**
     * Runs a host command with a hard timeout and captures its output.
     *
     * @param command   argv of the host command, usually the isolate binary plus its options
     * @param timeoutMs how long to wait for termination before the process is killed; must be positive
     * @return the captured result: exit code ({@code -1} when the watchdog fired), whether the process
     *         finished on its own, and stdout/stderr truncated to {@link #ISOLATE_OUTPUT_LIMIT}
     * @throws IOException          when the process cannot be started
     * @throws InterruptedException when the calling thread is interrupted while waiting
     */
    private ProcResult execProcess(List<String> command, long timeoutMs) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();
        StreamCollector stdout = new StreamCollector(process.getInputStream(), ISOLATE_OUTPUT_LIMIT);
        StreamCollector stderr = new StreamCollector(process.getErrorStream(), ISOLATE_OUTPUT_LIMIT);
        Thread stdoutThread = daemon(stdout, "isolate-stdout");
        Thread stderrThread = daemon(stderr, "isolate-stderr");

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        int exitCode = finished ? process.exitValue() : -1;
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
        stdoutThread.join(1000);
        stderrThread.join(1000);
        return new ProcResult(exitCode, finished, stdout.text(), stderr.text());
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

    /**
     * Translates host argv into sandbox argv.
     *
     * <p>The first token is the executable: a plain name is looked up on {@code PATH} (and resolved to its
     * real path so isolate never follows {@code /etc/alternatives} links), while a file inside the working
     * directory becomes {@code /box/<name>}. Later tokens are only rewritten when they point inside the
     * working directory.</p>
     *
     * @param argv host argv as configured
     * @param dir  host working directory mounted at {@code /box}
     * @return the argv to hand to isolate; never {@code null}
     */
    List<String> boxArgv(List<String> argv, Path dir) {
        if (argv.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(argv.size());
        for (int i = 0; i < argv.size(); i++) {
            String token = argv.get(i);
            result.add(i == 0 ? toSandboxExecutable(token, dir) : toSandboxArgument(token, dir));
        }
        return result;
    }

    /**
     * Resolves the executable token of a command.
     *
     * @param token host token, e.g. {@code ./main}, {@code /usr/bin/java} or {@code javac}
     * @param dir   host working directory
     * @return the sandbox path or host absolute path to execute
     */
    private String toSandboxExecutable(String token, Path dir) {
        if (token.startsWith("./") || token.startsWith("../") || token.startsWith("/")) {
            return toSandboxPath(dir.resolve(token).normalize(), dir);
        }
        Path candidate = dir.resolve(token);
        if (Files.isRegularFile(candidate)) {
            return toSandboxPath(candidate, dir);
        }
        return resolveExecutable(token);
    }

    /**
     * Rewrites an argument that refers to a file inside the working directory.
     *
     * @param token argument token
     * @param dir   host working directory
     * @return the sandbox path when the token points inside the directory, otherwise the token unchanged
     */
    private String toSandboxArgument(String token, Path dir) {
        if (token.startsWith("./") || token.startsWith("../") || token.startsWith("/")) {
            return toSandboxPath(dir.resolve(token).normalize(), dir);
        }
        return token;
    }

    /**
     * Maps a host path to the path the sandbox sees.
     *
     * @param hostPath host path inside or outside the working directory
     * @param dir      host working directory mounted at {@code /box}
     * @return {@code /box/<relative>} when the path is inside the directory, otherwise the absolute path
     */
    private String toSandboxPath(Path hostPath, Path dir) {
        Path absolute = hostPath.toAbsolutePath().normalize();
        Path base = dir.toAbsolutePath().normalize();
        if (absolute.startsWith(base)) {
            return BOX_DIR + "/" + base.relativize(absolute).toString().replace(File.separatorChar, '/');
        }
        return absolute.toString();
    }

    /**
     * Replaces a program name with its real host path so isolate never has to follow symlinks in
     * {@code /etc/alternatives}, which is not mounted in the sandbox.
     *
     * @param name executable name without a slash, e.g. {@code javac}
     * @return the real absolute path, or the name unchanged when resolution is disabled or fails
     */
    private String resolveExecutable(String name) {
        if (!properties.getIsolate().isResolveExecutables()) {
            return name;
        }
        String resolved = findOnPath(name);
        return resolved == null ? name : resolved;
    }

    /**
     * Looks up an executable on the host {@code PATH}.
     *
     * @param name executable name without a slash, e.g. {@code javac}
     * @return the real (symlink resolved) absolute path of the first match, or {@code null} when
     *         {@code PATH} is unset or no entry contains an executable with that name
     */
    private String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String entry : path.split(File.pathSeparator)) {
            Path candidate = Path.of(entry, name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                try {
                    return candidate.toRealPath().toString();
                } catch (IOException ex) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }

    /**
     * Formats a numeric isolate argument with two decimals using the root locale.
     *
     * @param value seconds or another numeric isolate limit
     * @return the value with exactly two decimals and a dot separator, as expected by isolate
     */
    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Captured result of one host process started by this sandbox.
     *
     * @param exitCode process exit code, or {@code -1} when the process had to be killed after the timeout
     * @param finished {@code true} when the process exited before the timeout
     * @param stdout   captured stdout, truncated to {@link #ISOLATE_OUTPUT_LIMIT}
     * @param stderr   captured stderr, truncated to {@link #ISOLATE_OUTPUT_LIMIT}
     */
    private record ProcResult(int exitCode, boolean finished, String stdout, String stderr) {

        /**
         * Combines both captured streams for logs and error messages.
         *
         * @return trimmed stdout; trimmed stderr when stdout is blank; or {@code "stdout | stderr"}
         */
        String output() {
            String error = stderr == null ? "" : stderr.trim();
            String out = stdout == null ? "" : stdout.trim();
            if (error.isEmpty()) {
                return out;
            }
            return out.isEmpty() ? error : out + " | " + error;
        }
    }
}
