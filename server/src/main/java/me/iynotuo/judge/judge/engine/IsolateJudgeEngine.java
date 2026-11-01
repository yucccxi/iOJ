package me.iynotuo.judge.judge.engine;

import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.judge.JudgeContext;
import me.iynotuo.judge.judge.JudgeOutcome;
import me.iynotuo.judge.judge.LanguageCommands;
import me.iynotuo.judge.judge.answer.AnswerChecker;
import me.iynotuo.judge.judge.answer.AnswerCheckerFactory;
import me.iynotuo.judge.judge.sandbox.Commands;
import me.iynotuo.judge.judge.sandbox.IsolateSandbox;
import me.iynotuo.judge.judge.sandbox.Limits;
import me.iynotuo.judge.judge.sandbox.Sandbox;
import me.iynotuo.judge.judge.sandbox.SandboxCommand;
import me.iynotuo.judge.judge.sandbox.SandboxFiles;
import me.iynotuo.judge.judge.sandbox.SandboxResult;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolate sandbox judge engine.
 *
 * <p>Owns the judging flow (compile once, run every test case, aggregate the results) but delegates all
 * process execution to {@link IsolateSandbox} and all answer comparison to an {@link AnswerChecker}. That
 * keeps this class free of both isolate command details and special judge policy.</p>
 *
 * <p>Requires the Isolate service to run and the service user to be allowed to execute
 * {@code /usr/bin/isolate} (usually membership in the {@code isolate} group).</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.judge", name = "engine", havingValue = "isolate")
public class IsolateJudgeEngine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(IsolateJudgeEngine.class);

    private final JudgeProperties properties;
    private final Sandbox sandbox;
    private final AnswerCheckerFactory checkerFactory;

    /**
     * Creates the engine and the sandbox it drives.
     *
     * @param properties     judge configuration; supplies limits, language commands and isolate settings
     * @param checkerFactory decides how answers are compared for each submission
     * @throws IllegalStateException when isolate is not executable and startup verification is enabled
     */
    public IsolateJudgeEngine(JudgeProperties properties, AnswerCheckerFactory checkerFactory) {
        this.properties = properties;
        this.sandbox = new IsolateSandbox(properties);
        this.checkerFactory = checkerFactory;
    }

    @Override
    public JudgeOutcome judge(JudgeContext context) {
        Path work = null;
        try {
            work = Files.createTempDirectory("ioj-isolate-");
            SandboxFiles.setPermissions(work, "rwxrwxrwx");

            AnswerChecker checker = checkerFactory.create(context, sandbox);
            JudgeOutcome preparation = checker.prepare(work);
            if (preparation != null) {
                return preparation;
            }

            Path compileDir = SandboxFiles.createDir(work.resolve("compile"));
            JudgeOutcome compileFailure = compile(context, compileDir);
            if (compileFailure != null) {
                return compileFailure;
            }

            JudgeProperties.Isolate isolate = properties.getIsolate();
            JudgeProperties.LanguageConfig language = context.language();
            int timeLimitMs = LanguageCommands.timeLimitMs(context);
            int memoryLimitMb = context.problem().getMemoryLimitMb();
            int processes = language.getIsolateProcesses() == null
                    ? 1 : Math.max(1, language.getIsolateProcesses());
            int extraMb = language.getIsolateMemoryExtraMb() == null
                    ? 0 : Math.max(0, language.getIsolateMemoryExtraMb());
            Limits runLimits = new Limits(timeLimitMs,
                    (int) Math.ceil(timeLimitMs / 1000.0) + Math.max(1, isolate.getWallTimeExtraSeconds()),
                    Math.max(16, memoryLimitMb + extraMb) * 1024,
                    Math.max(1024, properties.getMaxOutputBytes() / 1024 + 64));

            List<JudgeOutcome.CaseResult> results = new ArrayList<>();
            int maxTimeMs = 0;
            int maxMemoryKb = 0;
            SubmissionStatus overall = SubmissionStatus.ACCEPTED;

            for (ProblemTestcase testcase : context.testcases()) {
                Path runDir = SandboxFiles.createDir(work.resolve("run-" + testcase.getOrdinal()));
                SandboxFiles.copyArtifacts(compileDir, runDir, language.getSourceFile(),
                        StringUtils.hasText(language.getCompileCommand()));
                SandboxFiles.writeFile(runDir.resolve("input.txt"), testcase.getInputData(), "rw-rw-rw-");

                SandboxResult run = sandbox.exec(new SandboxCommand(runDir,
                        Commands.split(LanguageCommands.render(language.getRunCommand(), memoryLimitMb, timeLimitMs)),
                        runDir.resolve("input.txt"), runLimits, processes));

                SubmissionStatus status = classifyExecution(run);
                String message = caseMessage(status, run);
                if (status == SubmissionStatus.ACCEPTED) {
                    AnswerChecker.Verdict verdict = checker.verify(testcase, run.stdout(), work);
                    status = verdict.status();
                    message = verdict.message();
                }

                maxTimeMs = Math.max(maxTimeMs, run.timeMs());
                maxMemoryKb = Math.max(maxMemoryKb, run.memoryKb());
                results.add(new JudgeOutcome.CaseResult(testcase.getOrdinal(), status, run.timeMs(),
                        run.memoryKb() > 0 ? run.memoryKb() : null, message));
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
            SandboxFiles.deleteRecursively(work);
        }
    }

    /**
     * Compiles the submission inside the sandbox.
     *
     * @param context the judging run; its submission supplies the source code and its language the source
     *                file name, the compile command and the compile limits
     * @param dir     host working directory: the source file is written here and the compiled artifacts
     *                stay here for the run phase
     * @return {@code null} when compilation succeeded or the language has no compile step; otherwise a
     *         {@code COMPILATION_ERROR} outcome carrying the compiler output (or {@code Compile Time
     *         Exceeded} when the compile step ran out of time), or a {@code SYSTEM_ERROR} outcome when the
     *         sandbox itself failed
     * @throws Exception when the source file cannot be written or the sandbox cannot be started
     */
    private JudgeOutcome compile(JudgeContext context, Path dir) throws Exception {
        JudgeProperties.LanguageConfig language = context.language();
        SandboxFiles.writeFile(dir.resolve(language.getSourceFile()), context.submission().getCode(), "rw-r--r--");
        if (!StringUtils.hasText(language.getCompileCommand())) {
            return null;
        }

        JudgeProperties.Isolate isolate = properties.getIsolate();
        Limits limits = new Limits((int) Math.ceil(isolate.getCompileTimeSeconds() * 1000.0),
                (int) Math.ceil(isolate.getCompileWallTimeSeconds()),
                isolate.getCompileCgMemKb(), isolate.getCompileFsizeKb());
        SandboxResult result = sandbox.exec(new SandboxCommand(dir,
                Commands.split(language.getCompileCommand()), null, limits, isolate.getCompileProcesses()));

        if (!result.finished() || result.internalError()) {
            return JudgeOutcome.systemError("isolate failed while compiling");
        }
        if (result.timedOut()) {
            return JudgeOutcome.compilationError("Compile Time Exceeded");
        }
        if (result.exitCode() != 0) {
            String message = result.stderr().isBlank() ? result.stdout() : result.stderr();
            return JudgeOutcome.compilationError(message);
        }
        return null;
    }

    /**
     * Maps the raw sandbox result of one test case to a submission status, ignoring the expected answer.
     *
     * <p>The order of the checks is significant: a watchdog kill is a timeout, {@code XX} means the sandbox
     * itself failed, cgroup OOM is a memory limit (checked before a signal, because the kernel kills the
     * process group when memory is exhausted), {@code TO} is a CPU/wall timeout, an oversized output is an
     * output limit, and a signal or non-zero exit is a runtime error. A clean exit returns
     * {@code ACCEPTED} and the answer checker then decides whether the output is right.</p>
     *
     * @param run the finished run of this test case
     * @return the execution status; {@code ACCEPTED} only when the program exited cleanly within limits
     */
    private SubmissionStatus classifyExecution(SandboxResult run) {
        if (!run.finished()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (run.internalError()) {
            return SubmissionStatus.SYSTEM_ERROR;
        }
        if (run.outOfMemory()) {
            return SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        }
        if (run.timedOut()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (run.stdoutTruncated()) {
            return SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
        }
        if (run.runtimeError()) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        if (run.exitCode() != 0) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        return SubmissionStatus.ACCEPTED;
    }

    /**
     * Builds the human readable message stored for a failed case.
     *
     * <p>The messages are fixed English sentences without any measured value: the limits themselves are not
     * quoted, so a message does not reveal the concrete limit that was hit.</p>
     *
     * @param status status returned by {@link #classifyExecution}; accepted cases produce no message
     * @param run    the finished run, providing stderr and the terminating signal
     * @return a short reason for TLE/MLE/OLE, the trimmed stderr (or a signal/non-zero-exit hint) for a
     *         runtime error, and {@code null} for {@code ACCEPTED}/{@code WRONG_ANSWER} cases
     */
    private String caseMessage(SubmissionStatus status, SandboxResult run) {
        return switch (status) {
            case TIME_LIMIT_EXCEEDED -> "Time Limit Exceeded";
            case MEMORY_LIMIT_EXCEEDED -> "Memory Limit Exceeded";
            case OUTPUT_LIMIT_EXCEEDED -> "Output Limit Exceeded";
            case RUNTIME_ERROR -> {
                String message = run.stderr() == null ? "" : run.stderr().trim();
                if (!message.isEmpty()) {
                    yield message;
                }
                yield run.signal() > 0 ? "Killed by signal" : "Non-zero exit code";
            }
            default -> null;
        };
    }
}
