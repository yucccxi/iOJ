package me.iynotuo.judge.judge;

import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 本机进程判题引擎：编译 + 逐测试点运行。
 *
 * <p>隔离手段：独立临时目录、bash ulimit（内存/文件大小）、墙钟超时、输出上限。
 * 它适合校内小规模使用和本地开发；如果对公网开放注册，请换成 Docker / nsjail 引擎，
 * 因为本地进程无法完全限制 CPU 抢占、fork bomb、网络访问等行为。</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.judge", name = "engine", havingValue = "local", matchIfMissing = true)
public class LocalProcessJudgeEngine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(LocalProcessJudgeEngine.class);

    private static final int STDERR_LIMIT = 8 * 1024;
    private static final int MESSAGE_LIMIT = 1_000;

    private final JudgeProperties properties;

    public LocalProcessJudgeEngine(JudgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public JudgeOutcome judge(JudgeContext context) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("ioj-judge-");
        } catch (IOException ex) {
            return JudgeOutcome.systemError("无法创建判题临时目录: " + ex.getMessage());
        }
        try {
            return doJudge(context, workDir);
        } catch (Exception ex) {
            log.error("判题异常 submissionId={}", context.submission().getId(), ex);
            return JudgeOutcome.systemError("判题异常: " + ex.getClass().getSimpleName());
        } finally {
            deleteRecursively(workDir);
        }
    }

    private JudgeOutcome doJudge(JudgeContext context, Path workDir) throws Exception {
        JudgeProperties.LanguageConfig language = context.language();
        Files.writeString(workDir.resolve(language.getSourceFile()),
                context.submission().getCode(), StandardCharsets.UTF_8);

        if (StringUtils.hasText(language.getCompileCommand())) {
            CommandResult compile = execute(language.getCompileCommand(), workDir, null,
                    properties.getCompileTimeoutMs(), properties.getMaxCompileOutputBytes());
            if (compile.timedOut()) {
                return JudgeOutcome.compilationError("编译超时（>" + properties.getCompileTimeoutMs() + "ms）");
            }
            if (compile.exitCode() != 0) {
                return JudgeOutcome.compilationError(
                        truncate(compile.stderr().isBlank() ? compile.stdout() : compile.stderr(),
                                properties.getMaxCompileOutputBytes()));
            }
        }

        int timeLimitMs = timeLimit(context);
        int memoryLimitMb = context.problem().getMemoryLimitMb();
        List<JudgeOutcome.CaseResult> results = new ArrayList<>();
        int maxTimeMs = 0;
        SubmissionStatus overall = SubmissionStatus.ACCEPTED;
        for (ProblemTestcase testcase : context.testcases()) {
            CommandResult run = runCase(language, workDir, testcase, timeLimitMs, memoryLimitMb);
            SubmissionStatus status = evaluate(run, testcase.getOutputData());
            maxTimeMs = Math.max(maxTimeMs, run.timeMs());
            results.add(new JudgeOutcome.CaseResult(testcase.getOrdinal(), status, run.timeMs(),
                    null, caseMessage(run, status, timeLimitMs)));
            if (status != SubmissionStatus.ACCEPTED) {
                overall = status;
                // fail-fast：失败后不再跑后续测试点，节省机器资源
                break;
            }
        }
        if (results.isEmpty()) {
            return JudgeOutcome.systemError("题目没有可用测试点");
        }
        return new JudgeOutcome(overall, null, null, results, maxTimeMs, null);
    }

    private CommandResult runCase(JudgeProperties.LanguageConfig language, Path workDir,
                                  ProblemTestcase testcase, int timeLimitMs, int memoryLimitMb) throws Exception {
        Path inputFile = workDir.resolve("stdin.txt");
        Files.writeString(inputFile, testcase.getInputData() == null ? "" : testcase.getInputData(),
                StandardCharsets.UTF_8);
        String command = render(language.getRunCommand(), memoryLimitMb, timeLimitMs);
        String script = render(language.getRunWrapper(), memoryLimitMb, timeLimitMs)
                .replace("{command}", command);
        return execute(script, workDir, inputFile, timeLimitMs, properties.getMaxOutputBytes());
    }

    private CommandResult execute(String script, Path workDir, Path stdin, long timeoutMs, int outputLimit)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", script).directory(workDir.toFile());
        if (stdin != null) {
            builder.redirectInput(stdin.toFile());
        }
        long start = System.nanoTime();
        Process process = builder.start();
        process.getOutputStream().close();

        StreamCollector stdout = new StreamCollector(process.getInputStream(), outputLimit);
        StreamCollector stderr = new StreamCollector(process.getErrorStream(), STDERR_LIMIT);
        Thread stdoutThread = daemon(stdout, "judge-stdout");
        Thread stderrThread = daemon(stderr, "judge-stderr");

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        int timeMs = (int) Math.min((System.nanoTime() - start) / 1_000_000L, Integer.MAX_VALUE);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            stdoutThread.join(500);
            stderrThread.join(500);
            return new CommandResult(-1, "", "", true, timeMs, false);
        }
        stdoutThread.join(1000);
        stderrThread.join(1000);
        return new CommandResult(process.exitValue(), stdout.text(), stderr.text(), false, timeMs,
                stdout.truncated());
    }

    private Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private SubmissionStatus evaluate(CommandResult run, String expectedOutput) {
        if (run.timedOut()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (run.outputTruncated()) {
            return SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
        }
        if (run.exitCode() != 0) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        if (!OutputComparator.matches(expectedOutput, run.stdout())) {
            return SubmissionStatus.WRONG_ANSWER;
        }
        return SubmissionStatus.ACCEPTED;
    }

    private String caseMessage(CommandResult run, SubmissionStatus status, int timeLimitMs) {
        return switch (status) {
            case TIME_LIMIT_EXCEEDED -> "超过时间限制 " + timeLimitMs + "ms";
            case OUTPUT_LIMIT_EXCEEDED -> "输出超过 " + properties.getMaxOutputBytes() + " 字节";
            case RUNTIME_ERROR -> truncate(run.stderr().isBlank() ? "非零退出码 " + run.exitCode()
                    : run.stderr(), MESSAGE_LIMIT);
            default -> null;
        };
    }

    private int timeLimit(JudgeContext context) {
        double multiplier = context.language().getTimeMultiplier() <= 0
                ? 1.0 : context.language().getTimeMultiplier();
        int problemLimit = context.problem().getTimeLimitMs() == null
                ? 1000 : context.problem().getTimeLimitMs();
        return (int) Math.max(100, Math.ceil(problemLimit * multiplier));
    }

    private String render(String template, int memoryLimitMb, int timeLimitMs) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{memoryMb}", String.valueOf(memoryLimitMb))
                .replace("{memoryKb}", String.valueOf(memoryLimitMb * 1024L))
                .replace("{timeMs}", String.valueOf(timeLimitMs));
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "\n...（输出已截断）";
    }

    private void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 清理失败不影响判题结果
                }
            });
        } catch (IOException ex) {
            log.warn("清理判题临时目录失败: {}", dir, ex);
        }
    }

    private record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut,
                                 int timeMs, boolean outputTruncated) {
    }
}
