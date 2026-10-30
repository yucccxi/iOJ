package me.iynotuo.judge.judge.engine;

import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.judge.JudgeContext;
import me.iynotuo.judge.judge.JudgeOutcome;
import me.iynotuo.judge.judge.OutputComparator;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Isolate sandbox judge engine.
 *
 * <p>Compilation and execution both run inside Isolate boxes. The working directory of the
 * submission is bind-mounted at {@code /box}, so the worker never needs write access to the
 * Isolate box root. Each test case gets a fresh box and a fresh copy of the compiled artifacts,
 * which keeps cases independent from each other.</p>
 *
 * <p>Requires the Isolate service to run and the service user to be allowed to execute
 * {@code /usr/bin/isolate} (usually membership in the {@code isolate} group).</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.judge", name = "engine", havingValue = "isolate")
public class IsolateJudgeEngine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(IsolateJudgeEngine.class);

    private static final int ISOLATE_OUTPUT_LIMIT = 64 * 1024;
    private static final int MESSAGE_LIMIT = 1_000;
    private static final String BOX_DIR = "/box";

    private final JudgeProperties properties;
    private final Path binary;
    private final BlockingQueue<Integer> boxIds;

    /**
     * Creates the engine and validates the sandbox prerequisites.
     *
     * <p>The isolate binary must be executable by the service user; the check can be skipped with
     * {@code ioj.judge.isolate.check-on-startup=false} when the binary is only reachable at run time.</p>
     *
     * @param properties judge configuration; {@code properties.getIsolate()} provides the binary path,
     *                   the box id pool ({@code box-id-start} / {@code box-id-count}) and the setup
     *                   timeouts. Never {@code null}
     * @throws IllegalStateException when startup verification is enabled and the isolate binary is not
     *                               executable, so a misconfigured deployment fails fast instead of
     *                               failing every submission
     */
    public IsolateJudgeEngine(JudgeProperties properties) {
        this.properties = properties;
        JudgeProperties.Isolate isolate = properties.getIsolate();
        this.binary = Path.of(isolate.getBinary());
        if (isolate.isCheckOnStartup() && !Files.isExecutable(binary)) {
            throw new IllegalStateException("isolate binary is not executable: " + binary
                    + ". Add the service user to the 'isolate' group and log in again: "
                    + "sudo usermod -aG isolate <user>");
        }
        int count = Math.max(1, isolate.getBoxIdCount());
        this.boxIds = new ArrayBlockingQueue<>(count);
        for (int i = 0; i < count; i++) {
            boxIds.add(isolate.getBoxIdStart() + i);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes: takes one box id from the pool (blocking while all boxes are busy), creates a
     * temporary working directory, compiles once, then runs every test case in a fresh box. Judging stops at
     * the first non-accepted case. The working directory and the box id are always released in a
     * {@code finally} block, so neither a compile error nor an unexpected exception leaks sandbox state.</p>
     *
     * @param context submission, problem limits, ordered test cases and resolved language configuration of
     *                this run; never {@code null}. An empty test case list yields a {@code SYSTEM_ERROR}
     * @return the aggregated outcome; never {@code null}. {@code cases} holds one entry per executed test
     *         case in order (stopping at the first failure), {@code timeUsedMs} / {@code memoryUsedKb} are
     *         the maxima over those cases, and {@code memoryUsedKb} is {@code null} when no case reported
     *         cgroup memory usage. Any unexpected exception, including a failed {@code isolate --init},
     *         is converted into a {@code SYSTEM_ERROR} outcome instead of being propagated
     */
    @Override
    public JudgeOutcome judge(JudgeContext context) {
        int boxId;
        try {
            boxId = boxIds.take();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return JudgeOutcome.systemError("interrupted while waiting for an isolate box");
        }
        Path work = null;
        try {
            work = Files.createTempDirectory("ioj-isolate-");
            setPermissions(work, "rwxrwxrwx");
            Path compileDir = createDir(work.resolve("compile"));

            JudgeOutcome compileFailure = compile(context, boxId, compileDir);
            if (compileFailure != null) {
                return compileFailure;
            }

            JudgeProperties.Isolate isolate = properties.getIsolate();
            JudgeProperties.LanguageConfig language = context.language();
            int timeLimitMs = timeLimit(context);
            int memoryLimitMb = context.problem().getMemoryLimitMb();
            int processes = language.getIsolateProcesses() == null
                    ? 1 : Math.max(1, language.getIsolateProcesses());
            int extraMb = language.getIsolateMemoryExtraMb() == null
                    ? 0 : Math.max(0, language.getIsolateMemoryExtraMb());
            int cgMemKb = Math.max(16, memoryLimitMb + extraMb) * 1024;
            int wallSeconds = (int) Math.ceil(timeLimitMs / 1000.0)
                    + Math.max(1, isolate.getWallTimeExtraSeconds());
            int fsizeKb = Math.max(1024, properties.getMaxOutputBytes() / 1024 + 64);

            List<JudgeOutcome.CaseResult> results = new ArrayList<>();
            int maxTimeMs = 0;
            int maxMemoryKb = 0;
            SubmissionStatus overall = SubmissionStatus.ACCEPTED;

            for (ProblemTestcase testcase : context.testcases()) {
                Path runDir = createDir(work.resolve("run-" + testcase.getOrdinal()));
                copyArtifacts(compileDir, runDir, language.getSourceFile(),
                        StringUtils.hasText(language.getCompileCommand()));
                writeFile(runDir.resolve("input.txt"), testcase.getInputData(), "rw-rw-rw-");
                Path output = runDir.resolve("output.txt");
                Path stderr = runDir.resolve("stderr.txt");
                writeFile(output, "", "rw-rw-rw-");
                writeFile(stderr, "", "rw-rw-rw-");
                Path metaFile = work.resolve("run-" + testcase.getOrdinal() + ".meta");

                ProcResult process;
                initBox(boxId);
                try {
                    List<String> command = runCommand(boxId, runDir, metaFile, language,
                            processes, timeLimitMs, wallSeconds, cgMemKb, fsizeKb, memoryLimitMb);
                    process = exec(command, (wallSeconds + 10L) * 1000L);
                } finally {
                    cleanupBox(boxId);
                }

                IsolateMeta meta = IsolateMeta.parse(metaFile);
                SubmissionStatus status = classify(process, meta, output, testcase.getOutputData());
                int timeMs = meta.timeMs();
                int memoryKb = meta.memoryKb();
                maxTimeMs = Math.max(maxTimeMs, timeMs);
                maxMemoryKb = Math.max(maxMemoryKb, memoryKb);
                results.add(new JudgeOutcome.CaseResult(testcase.getOrdinal(), status, timeMs,
                        memoryKb > 0 ? memoryKb : null, caseMessage(status, meta, stderr)));
                if (status != SubmissionStatus.ACCEPTED) {
                    overall = status;
                    break;
                }
            }

            if (results.isEmpty()) {
                return JudgeOutcome.systemError("problem has no test case");
            }
            return new JudgeOutcome(overall, null, null, results, maxTimeMs,
                    maxMemoryKb > 0 ? maxMemoryKb : null);
        } catch (Exception ex) {
            log.error("Isolate judging failed for submission {}", context.submission().getId(), ex);
            return JudgeOutcome.systemError("isolate judge error: " + ex.getClass().getSimpleName());
        } finally {
            if (work != null) {
                deleteRecursively(work);
            }
            boxIds.offer(boxId);
        }
    }

    /**
     * Compiles the submission inside a fresh box.
     *
     * @param context the judging run; its submission supplies the source code and its language the source
     *                file name, the compile command and the compile limits
     * @param boxId   id of the isolate box used for compiling; the caller owns the id and must not use it
     *                concurrently
     * @param dir     host working directory bind-mounted as {@code /box}: the source file is written here
     *                and the compiled artifacts stay here for the run phase
     * @return {@code null} when compilation succeeded or the language has no compile step; otherwise a
     *         {@code COMPILATION_ERROR} outcome carrying the compiler output (or "compile timed out"), or a
     *         {@code SYSTEM_ERROR} outcome when isolate itself failed
     * @throws Exception when the source file cannot be written or the isolate process cannot be started
     */
    private JudgeOutcome compile(JudgeContext context, int boxId, Path dir) throws Exception {
        JudgeProperties.LanguageConfig language = context.language();
        JudgeProperties.Isolate isolate = properties.getIsolate();
        Path compileOut = dir.resolve("compile.out");
        Path compileErr = dir.resolve("compile.err");
        writeFile(dir.resolve(language.getSourceFile()), context.submission().getCode(), "rw-r--r--");
        writeFile(dir.resolve("empty.txt"), "", "rw-rw-rw-");
        writeFile(compileOut, "", "rw-rw-rw-");
        writeFile(compileErr, "", "rw-rw-rw-");
        Path metaFile = dir.getParent().resolve("compile.meta");

        if (!StringUtils.hasText(language.getCompileCommand())) {
            return null;
        }

        ProcResult process;
        initBox(boxId);
        try {
            List<String> command = isolateCommand(boxId, dir, BOX_DIR + "/empty.txt",
                    BOX_DIR + "/compile.out", BOX_DIR + "/compile.err", metaFile,
                    isolate.getCompileProcesses(), isolate.getCompileTimeSeconds(),
                    isolate.getCompileWallTimeSeconds(), isolate.getCompileCgMemKb(),
                    isolate.getCompileFsizeKb(), resolveProgram(splitCommand(language.getCompileCommand())));
            process = exec(command, (long) (isolate.getCompileWallTimeSeconds() * 1000) + 10_000);
        } finally {
            cleanupBox(boxId);
        }

        IsolateMeta meta = IsolateMeta.parse(metaFile);
        if (!process.finished() || "XX".equals(meta.status())) {
            return JudgeOutcome.systemError("isolate failed while compiling");
        }
        if ("TO".equals(meta.status())) {
            return JudgeOutcome.compilationError("编译超时");
        }
        if (process.exitCode() != 0 || meta.exitCode() != 0) {
            String message = readText(compileErr, properties.getMaxCompileOutputBytes());
            if (message.isBlank()) {
                message = readText(compileOut, properties.getMaxCompileOutputBytes());
            }
            return JudgeOutcome.compilationError(message);
        }
        return null;
    }

    /**
     * Maps one executed test case to a submission status.
     *
     * <p>The order of the checks is significant: a watchdog kill is a timeout, {@code XX} means isolate
     * itself failed, cgroup OOM is a memory limit (checked before {@code SG}, because the kernel kills the
     * process group when memory is exhausted), {@code TO} is a CPU/wall timeout, an oversized output file is
     * an output limit, then a signal or non-zero exit is a runtime error, and only a clean exit with
     * matching output is accepted.</p>
     *
     * @param process  the finished {@code isolate --run} invocation; {@code finished=false} means the
     *                 watchdog in {@link #exec(List, long)} killed it
     * @param meta     parsed {@code --meta} file of the run; supplies status, exit code, signal and OOM flag
     * @param output   host-side path of the file bound to {@code /box/output.txt}, i.e. the program's stdout;
     *                 its size is used for the output limit check
     * @param expected expected output of the test case, compared with {@link OutputComparator}
     * @return the status of this single test case; never {@code null}
     */
    private SubmissionStatus classify(ProcResult process, IsolateMeta meta, Path output, String expected) {
        if (!process.finished()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if ("XX".equals(meta.status())) {
            return SubmissionStatus.SYSTEM_ERROR;
        }
        if (meta.outOfMemory()) {
            return SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        }
        if ("TO".equals(meta.status())) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (size(output) >= properties.getMaxOutputBytes()) {
            return SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
        }
        if ("SG".equals(meta.status()) || "RE".equals(meta.status())) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        if (meta.exitCode() != 0) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        if (!OutputComparator.matches(expected, readText(output, properties.getMaxOutputBytes()))) {
            return SubmissionStatus.WRONG_ANSWER;
        }
        return SubmissionStatus.ACCEPTED;
    }

    /**
     * Builds the human readable message stored for a failed case.
     *
     * <p>The messages are fixed English sentences without any measured value: the limits themselves are not
     * quoted, so a message does not reveal the concrete limit that was hit.</p>
     *
     * @param status status returned by {@link #classify}; accepted cases produce no message
     * @param meta   parsed meta of the run; it only distinguishes a signal from a non-zero exit code when the
     *               program wrote no stderr
     * @param stderr host-side path of the file bound to {@code /box/stderr.txt}
     * @return a short reason for TLE/MLE/OLE, the trimmed stderr (or a signal/non-zero-exit hint) for a
     *         runtime error, and {@code null} for {@code ACCEPTED}/{@code WRONG_ANSWER} cases
     */
    private String caseMessage(SubmissionStatus status, IsolateMeta meta, Path stderr) {
        return switch (status) {
            case TIME_LIMIT_EXCEEDED -> "Time Limit Exceeded";
            case MEMORY_LIMIT_EXCEEDED -> "Memory Limit Exceeded";
            case OUTPUT_LIMIT_EXCEEDED -> "Output Limit Exceeded";
            case RUNTIME_ERROR -> {
                String message = readText(stderr, MESSAGE_LIMIT).trim();
                if (!message.isEmpty()) {
                    yield message;
                }
                yield meta.signal() > 0 ? "Killed by signal" : "Non-zero exit code";
            }
            default -> null;
        };
    }

    /**
     * Resolves the per-case time limit applied inside the sandbox.
     *
     * @param context the judging run; the problem time limit is multiplied by the language time multiplier
     *                so slower languages (for example Java) get proportionally more time
     * @return the limit in milliseconds, rounded up and clamped to at least 100 ms
     */
    private int timeLimit(JudgeContext context) {
        double multiplier = context.language().getTimeMultiplier() <= 0
                ? 1.0 : context.language().getTimeMultiplier();
        int problemLimit = context.problem().getTimeLimitMs() == null
                ? 1000 : context.problem().getTimeLimitMs();
        return (int) Math.max(100, Math.ceil(problemLimit * multiplier));
    }

    /**
     * Builds the isolate command line for one test case.
     *
     * @param boxId         id of the initialized box the case runs in
     * @param dir           host directory bound at {@code /box} containing the compiled artifacts and
     *                      {@code input.txt}
     * @param metaFile      host path where isolate writes the run meta, later parsed by {@link IsolateMeta}
     * @param language      language configuration providing the run command template with the
     *                      {@code {memoryMb}} / {@code {memoryKb}} / {@code {timeMs}} placeholders
     * @param processes     max processes/threads inside the box; JVM-based languages need a value well
     *                      above 1 or {@code pthread_create} fails
     * @param timeLimitMs   CPU time limit in milliseconds, passed to isolate in seconds
     * @param wallSeconds   wall clock limit in seconds; kept above the CPU limit so a blocked program
     *                      still terminates
     * @param cgMemKb       memory limit in KB passed as {@code --cg-mem}
     * @param fsizeKb       max file size in KB passed as {@code --fsize}, bounding stdout growth
     * @param memoryLimitMb problem memory limit in MB substituted into the run command template, e.g. for
     *                      {@code -Xmx}
     * @return the complete argv including the resolved program path; never empty
     */
    private List<String> runCommand(int boxId, Path dir, Path metaFile,
                                    JudgeProperties.LanguageConfig language, int processes,
                                    int timeLimitMs, int wallSeconds, int cgMemKb, int fsizeKb,
                                    int memoryLimitMb) {
        String run = language.getRunCommand()
                .replace("{memoryMb}", String.valueOf(memoryLimitMb))
                .replace("{memoryKb}", String.valueOf(memoryLimitMb * 1024L))
                .replace("{timeMs}", String.valueOf(timeLimitMs));
        return isolateCommand(boxId, dir, BOX_DIR + "/input.txt", BOX_DIR + "/output.txt",
                BOX_DIR + "/stderr.txt", metaFile, processes, timeLimitMs / 1000.0,
                wallSeconds, cgMemKb, fsizeKb, resolveProgram(splitCommand(run)));
    }

    /**
     * Assembles a complete {@code isolate --run} argv.
     *
     * <p>Adds the cgroup resource limits, a minimal environment ({@code PATH}, {@code TMPDIR},
     * {@code LANG}), the writable bind mount for {@code /box}, one read-only bind mount per
     * {@code ioj.judge.isolate.extra-dirs} entry, stdio redirection and the meta file.</p>
     *
     * @param boxId       id of the initialized box to run in
     * @param dir         host directory exposed writable as {@code /box}, so artifacts survive the box
     * @param stdin       sandbox path of the stdin file, e.g. {@code /box/input.txt}
     * @param stdout      sandbox path of the stdout file, e.g. {@code /box/output.txt}
     * @param stderr      sandbox path of the stderr file, e.g. {@code /box/stderr.txt}
     * @param metaFile    host path of the meta file; its directory must be writable by the service user
     * @param processes   max processes/threads inside the box; values below 1 are clamped to 1
     * @param cpuSeconds  CPU time limit in seconds
     * @param wallSeconds wall clock limit in seconds
     * @param cgMemKb     memory limit in KB; values below 1024 are clamped to 1024
     * @param fsizeKb     max output file size in KB; values below 64 are clamped to 64
     * @param program     program and arguments to execute inside the box; must be non-empty
     * @return a new mutable argv list whose first element is the isolate binary
     */
    private List<String> isolateCommand(int boxId, Path dir, String stdin, String stdout, String stderr,
                                        Path metaFile, int processes, double cpuSeconds, double wallSeconds,
                                        int cgMemKb, int fsizeKb, List<String> program) {
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.add("--cg");
        command.add("--box-id=" + boxId);
        command.add("--processes=" + Math.max(1, processes));
        command.add("--time=" + format(cpuSeconds));
        command.add("--wall-time=" + format(wallSeconds));
        command.add("--cg-mem=" + Math.max(1024, cgMemKb));
        command.add("--fsize=" + Math.max(64, fsizeKb));
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
     * Initializes and implicitly cleans the given box before a run.
     *
     * @param boxId box id owned by this engine; must not be used by another thread at the same time
     * @throws IOException          when the isolate process cannot be started
     * @throws InterruptedException         when the calling thread is interrupted while waiting for isolate
     * @throws IllegalStateException        when isolate exits non-zero or prints no existing box root,
     *                                      typically because the box belongs to another user or the
     *                                      {@code isolate.service} cgroup backend is down
     */
    private void initBox(int boxId) throws IOException, InterruptedException {
        JudgeProperties.Isolate isolate = properties.getIsolate();
        ProcResult result = exec(List.of(binary.toString(), "--cg", "--box-id=" + boxId, "--init"),
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
            ProcResult result = exec(List.of(binary.toString(), "--cg", "--box-id=" + boxId, "--cleanup"),
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
     * @return the captured result: the exit code ({@code -1} when the watchdog fired), whether the process
     *         finished on its own, and stdout/stderr truncated to {@link #ISOLATE_OUTPUT_LIMIT}. Never
     *         {@code null}
     * @throws IOException          when the process cannot be started
     * @throws InterruptedException when the calling thread is interrupted while waiting
     */
    private ProcResult exec(List<String> command, long timeoutMs) throws IOException, InterruptedException {
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
     * Splits a configured command template on whitespace.
     *
     * @param command command line such as {@code "javac -encoding UTF-8 -d . Main.java"}
     * @return the non-empty tokens in order. Quoting is not interpreted because the templates come from
     *         configuration, never from user input
     */
    private List<String> splitCommand(String command) {
        return Arrays.stream(command.trim().split("\\s+"))
                .filter(part -> !part.isEmpty())
                .toList();
    }

    /**
     * Replaces the program name with its real host path so Isolate never has to follow
     * symlinks in /etc/alternatives, which is not mounted in the sandbox.
     *
     * @param program argv whose first element is the executable to resolve; later elements are left as-is
     * @return the same list when resolution is disabled, the program already contains a slash or the
     *         executable is not found on {@code PATH}; otherwise a copy whose first element is the resolved
     *         absolute path
     */
    private List<String> resolveProgram(List<String> program) {
        if (program.isEmpty() || !properties.getIsolate().isResolveExecutables()) {
            return program;
        }
        String executable = program.get(0);
        if (executable.contains("/")) {
            return program;
        }
        String resolved = findOnPath(executable);
        if (resolved == null) {
            return program;
        }
        List<String> result = new ArrayList<>(program);
        result.set(0, resolved);
        return result;
    }

    /**
     * Looks up an executable on the host {@code PATH}.
     *
     * @param name executable name without a slash, e.g. {@code javac}
     * @return the real (symlink-resolved) absolute path of the first match, or {@code null} when
     *         {@code PATH} is unset or no entry contains an executable with that name
     */
    private String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String dir : path.split(File.pathSeparator)) {
            Path candidate = Path.of(dir, name);
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
     * Creates a directory including its parents and widens its permissions so the sandboxed user can use it.
     *
     * @param dir directory to create
     * @return the same path, which allows chaining
     * @throws IOException when the directory cannot be created
     */
    private Path createDir(Path dir) throws IOException {
        Files.createDirectories(dir);
        setPermissions(dir, "rwxrwxrwx");
        return dir;
    }

    /**
     * Writes a UTF-8 text file, treating {@code null} content as an empty string.
     *
     * @param file        target file; its parent directory must already exist
     * @param content     text to write, {@code null} becomes {@code ""}
     * @param permissions POSIX permissions applied afterwards, e.g. {@code "rw-rw-rw-"} so the sandboxed
     *                    user can write the file; ignored on non-POSIX file systems
     * @throws IOException when the file cannot be written
     */
    private void writeFile(Path file, String content, String permissions) throws IOException {
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        setPermissions(file, permissions);
    }

    /**
     * Applies POSIX permissions on a best-effort basis.
     *
     * @param path        file or directory to adjust
     * @param permissions symbolic permissions such as {@code "rwxrwxrwx"} or {@code "rw-r--r--"}; failures
     *                    are ignored on file systems without POSIX permissions
     */
    private void setPermissions(Path path, String permissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX file systems keep their defaults.
        }
    }

    /**
     * Copies the compile output into a fresh per-case directory.
     *
     * @param from       compile directory produced by {@link #compile}
     * @param to         per-case directory that becomes {@code /box}
     * @param sourceFile source file name, excluded when {@code skipSource} is set
     * @param skipSource {@code true} for compiled languages, where the produced binary is used instead of the
     *                   source; {@code false} for interpreted languages, which must see the source at run time
     * @throws IOException when a file cannot be copied
     */
    private void copyArtifacts(Path from, Path to, String sourceFile, boolean skipSource) throws IOException {
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file) || (skipSource && name.equals(sourceFile))
                        || name.equals("compile.out") || name.equals("compile.err")
                        || name.equals("empty.txt")) {
                    continue;
                }
                Path target = to.resolve(name);
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                setPermissions(target, "rwxr-xr-x");
            }
        }
    }

    /**
     * Reads at most {@code limit} bytes of a UTF-8 text file.
     *
     * @param file  file to read; {@code null} or a missing file yields an empty string
     * @param limit upper bound in bytes, so a program producing huge output cannot exhaust the judge's memory
     * @return the decoded prefix of the file, or {@code ""} when the file is missing or unreadable
     */
    private String readText(Path file, int limit) {
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        try {
            long length = Files.size(file);
            int toRead = (int) Math.min(length, Math.max(0, limit));
            byte[] data = new byte[toRead];
            try (InputStream in = Files.newInputStream(file)) {
                int offset = 0;
                while (offset < toRead) {
                    int read = in.read(data, offset, toRead - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                return new String(data, 0, offset, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            return "";
        }
    }

    /**
     * Returns the size of a file without throwing.
     *
     * @param file file to inspect
     * @return size in bytes, or {@code 0} when the file is missing or unreadable
     */
    private long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return 0;
        }
    }

    /**
     * Deletes a directory tree, ignoring individual failures.
     *
     * @param dir root of the tree, normally the temporary work directory of one submission
     */
    private void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort cleanup; the directory lives under the system temp directory.
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to clean up isolate work directory {}", dir, ex);
        }
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
     * Captured result of one host process started by {@link #exec(List, long)}.
     *
     * @param exitCode process exit code, or {@code -1} when the process had to be killed after the timeout
     * @param finished {@code true} when the process exited before the timeout, i.e. {@code exitCode} is real
     * @param stdout   captured stdout, truncated to {@link IsolateJudgeEngine#ISOLATE_OUTPUT_LIMIT}
     * @param stderr   captured stderr, truncated to {@link IsolateJudgeEngine#ISOLATE_OUTPUT_LIMIT}
     */
    private record ProcResult(int exitCode, boolean finished, String stdout, String stderr) {

        /**
         * Combines both captured streams for logs and error messages.
         *
         * @return trimmed stdout; trimmed stderr when stdout is blank; or {@code "stdout | stderr"} when both
         *         are non-empty. Never {@code null}
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
