package me.iynotuo.judge.judge.engine;

import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.judge.JudgeContext;
import me.iynotuo.judge.judge.JudgeOutcome;
import me.iynotuo.judge.judge.LanguageCommands;
import me.iynotuo.judge.judge.answer.AnswerChecker;
import me.iynotuo.judge.judge.answer.AnswerCheckerFactory;
import me.iynotuo.judge.judge.sandbox.Commands;
import me.iynotuo.judge.judge.sandbox.Limits;
import me.iynotuo.judge.judge.sandbox.LocalSandbox;
import me.iynotuo.judge.judge.sandbox.Sandbox;
import me.iynotuo.judge.judge.sandbox.SandboxCommand;
import me.iynotuo.judge.judge.sandbox.SandboxFiles;
import me.iynotuo.judge.judge.sandbox.SandboxResult;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local process judge engine: compiles the code and runs every test case on the host.
 *
 * <p>Owns the judging flow only; process execution lives in {@link LocalSandbox} and answer comparison in
 * an {@link AnswerChecker}. The sandbox is a temporary directory plus bash {@code ulimit} and a wall-clock
 * timeout, which suits club deployments and development machines but cannot stop CPU starvation, fork
 * bombs or network access.</p>
 *
 * <p>Custom checkers are deliberately not supported: compiling and running author-supplied code without a
 * sandbox would hand every problem author arbitrary code execution on the judge host. The factory therefore
 * injects a checker that reports {@code CHECKER_ERROR}; special judge requires the isolate engine.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.judge", name = "engine", havingValue = "local", matchIfMissing = true)
public class LocalProcessJudgeEngine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(LocalProcessJudgeEngine.class);

    private static final int MESSAGE_LIMIT = 1_000;

    private final JudgeProperties properties;
    private final Sandbox sandbox;
    private final AnswerCheckerFactory checkerFactory;

    /**
     * Creates the fallback engine that runs submissions directly on the host.
     *
     * @param properties     judge configuration; supplies the per-language commands and limits
     * @param checkerFactory decides how answers are compared for each submission
     */
    public LocalProcessJudgeEngine(JudgeProperties properties, AnswerCheckerFactory checkerFactory) {
        this.properties = properties;
        this.sandbox = new LocalSandbox(properties);
        this.checkerFactory = checkerFactory;
    }

    @Override
    public JudgeOutcome judge(JudgeContext context) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("ioj-judge-");
        } catch (Exception ex) {
            return JudgeOutcome.systemError("无法创建判题临时目录: " + ex.getMessage());
        }
        try {
            return doJudge(context, workDir);
        } catch (Exception ex) {
            log.error("判题异常 submissionId={}", context.submission().getId(), ex);
            return JudgeOutcome.systemError("判题异常: " + ex.getClass().getSimpleName());
        } finally {
            SandboxFiles.deleteRecursively(workDir);
        }
    }

    /**
     * Judges one submission in the local sandbox.
     *
     * @param context the judging run: submission, problem, test cases and language configuration
     * @param workDir temporary directory shared by the compile step and every test case
     * @return the judging outcome, never {@code null}
     * @throws Exception when a file cannot be written or the sandbox cannot be started
     */
    private JudgeOutcome doJudge(JudgeContext context, Path workDir) throws Exception {
        JudgeProperties.LanguageConfig language = context.language();
        AnswerChecker checker = checkerFactory.create(context, sandbox);
        JudgeOutcome preparation = checker.prepare(workDir);
        if (preparation != null) {
            return preparation;
        }

        SandboxFiles.writeFile(workDir.resolve(language.getSourceFile()), context.submission().getCode(), "rw-r--r--");
        if (LanguageCommands.requiresCompile(language)) {
            JudgeOutcome compileFailure = compile(language, workDir);
            if (compileFailure != null) {
                return compileFailure;
            }
        }

        int timeLimitMs = LanguageCommands.timeLimitMs(context);
        int memoryLimitMb = context.problem().getMemoryLimitMb();
        String runCommand = LanguageCommands.render(language.getRunCommand(), memoryLimitMb, timeLimitMs);
        String runWrapper = LanguageCommands.render(language.getRunWrapper(), memoryLimitMb, timeLimitMs);

        List<JudgeOutcome.CaseResult> results = new ArrayList<>();
        int maxTimeMs = 0;
        SubmissionStatus overall = SubmissionStatus.ACCEPTED;
        for (ProblemTestcase testcase : context.testcases()) {
            Path inputFile = workDir.resolve("stdin.txt");
            SandboxFiles.writeFile(inputFile, testcase.getInputData(), "rw-rw-rw-");
            SandboxResult run = sandbox.exec(new SandboxCommand(workDir, Commands.split(runCommand), inputFile,
                    new Limits(timeLimitMs, (int) Math.ceil(timeLimitMs / 1000.0) + 3, 0, 0), 1, runWrapper));

            SubmissionStatus status = evaluate(run);
            String message = caseMessage(run, status, timeLimitMs);
            if (status == SubmissionStatus.ACCEPTED) {
                AnswerChecker.Verdict verdict = checker.verify(testcase, run.stdout(), workDir);
                status = verdict.status();
                message = verdict.message();
            }

            maxTimeMs = Math.max(maxTimeMs, run.timeMs());
            results.add(new JudgeOutcome.CaseResult(testcase.getOrdinal(), status, run.timeMs(), null, message));
            if (status != SubmissionStatus.ACCEPTED) {
                overall = status;
                // Fail fast: stop after the first failing test case to save machine time
                break;
            }
        }
        if (results.isEmpty()) {
            return JudgeOutcome.systemError("题目没有可用测试点");
        }
        return new JudgeOutcome(overall, null, null, results, maxTimeMs, null);
    }

    /**
     * Compiles the submission in the local sandbox.
     *
     * @param language language configuration with the compile command
     * @param workDir  directory that receives the source file and the compiled artifacts
     * @return {@code null} when compilation succeeded, otherwise a compilation error outcome
     * @throws Exception when the sandbox cannot be started
     */
    private JudgeOutcome compile(JudgeProperties.LanguageConfig language, Path workDir) throws Exception {
        int compileTimeoutMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1_000L, properties.getCompileTimeoutMs()));
        SandboxResult result = sandbox.exec(new SandboxCommand(workDir, Commands.split(language.getCompileCommand()),
                null, new Limits(compileTimeoutMs, (int) Math.ceil(compileTimeoutMs / 1000.0) + 3, 0, 0), 1));
        if (result.timedOut()) {
            return JudgeOutcome.compilationError("Compile Time Exceeded");
        }
        if (result.exitCode() != 0) {
            String message = result.stderr().isBlank() ? result.stdout() : result.stderr();
            return JudgeOutcome.compilationError(truncate(message, properties.getMaxCompileOutputBytes()));
        }
        return null;
    }

    /**
     * Maps the raw sandbox result to a submission status, ignoring the expected answer.
     *
     * @param run the finished run of one test case
     * @return the execution status; {@code ACCEPTED} only when the program exited cleanly within limits
     */
    private SubmissionStatus evaluate(SandboxResult run) {
        if (run.timedOut()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (run.stdoutTruncated()) {
            return SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
        }
        if (run.exitCode() != 0) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        return SubmissionStatus.ACCEPTED;
    }

    /**
     * Builds the message stored for a failed case.
     *
     * @param run         the finished run
     * @param status      status returned by {@link #evaluate}
     * @param timeLimitMs the resolved time limit, used in the timeout message
     * @return a short reason, or {@code null} for accepted cases
     */
    private String caseMessage(SandboxResult run, SubmissionStatus status, int timeLimitMs) {
        return switch (status) {
            case TIME_LIMIT_EXCEEDED -> "超过时间限制 " + timeLimitMs + "ms";
            case OUTPUT_LIMIT_EXCEEDED -> "输出超过 " + properties.getMaxOutputBytes() + " 字节";
            case RUNTIME_ERROR -> truncate(run.stderr().isBlank() ? "非零退出码 " + run.exitCode()
                    : run.stderr(), MESSAGE_LIMIT);
            default -> null;
        };
    }

    /**
     * Clamps free text to a maximum length.
     *
     * @param text  text to clamp; {@code null} becomes {@code null}
     * @param limit maximum number of characters
     * @return the possibly truncated text with a marker, or {@code null} when the input was {@code null}
     */
    private String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "\n...（输出已截断）";
    }
}
