package me.iynotuo.judge.judge.answer;

import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.judge.JudgeOutcome;
import me.iynotuo.judge.judge.sandbox.Commands;
import me.iynotuo.judge.judge.sandbox.Limits;
import me.iynotuo.judge.judge.sandbox.Sandbox;
import me.iynotuo.judge.judge.sandbox.SandboxCommand;
import me.iynotuo.judge.judge.sandbox.SandboxFiles;
import me.iynotuo.judge.judge.sandbox.SandboxResult;
import me.iynotuo.judge.problem.entity.ProblemTestcase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checker that runs the problem's own C++ checker, uploaded by the problem author.
 *
 * <p>The source is compiled once per submission by {@link #prepare(Path)}, then run once per test case.
 * Each case gets a fresh directory containing the checker binary, the test input, a copy of the contestant
 * output and the official answer. Keeping the answer in a directory the contestant program never touched
 * is what stops a submission from reading it.</p>
 *
 * <p>Verdict protocol: exit code {@code 0} accept, {@code 1} wrong answer, {@code 2} presentation error
 * (reported as a wrong answer), anything else is a broken checker and therefore {@code CHECKER_ERROR}.</p>
 */
public class ProgramAnswerChecker implements AnswerChecker {

    private static final String BINARY = "checker";
    private static final String INPUT_FILE = "input.txt";
    private static final String OUTPUT_FILE = "output.txt";
    private static final String ANSWER_FILE = "answer.txt";
    private static final int WALL_EXTRA_SECONDS = 3;
    private static final int COMPILE_PROCESSES = 64;
    private static final int FSIZE_KB = 16 * 1024;
    private static final int MIN_LIMIT_MS = 100;

    private final Sandbox sandbox;
    private final JudgeProperties properties;
    private final JudgeProperties.Checker config;
    private final String source;

    private Path buildDir;

    /**
     * Creates the checker.
     *
     * @param sandbox   sandbox used to compile and run the checker; must support author code
     * @param properties judge configuration, used for the compile output size cap
     * @param source    checker source code; never blank
     */
    public ProgramAnswerChecker(Sandbox sandbox, JudgeProperties properties, String source) {
        this.sandbox = sandbox;
        this.properties = properties;
        this.config = properties.getChecker();
        this.source = source;
    }

    @Override
    public String name() {
        return "program";
    }

    @Override
    public JudgeOutcome prepare(Path workDir) throws Exception {
        buildDir = SandboxFiles.createDir(workDir.resolve("checker-build"));
        SandboxFiles.writeFile(buildDir.resolve(config.getSourceFile()), source, "rw-r--r--");

        int timeMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1_000L, config.getCompileTimeoutMs()));
        Limits limits = new Limits(timeMs, (int) Math.ceil(timeMs / 1000.0) + WALL_EXTRA_SECONDS,
                Math.max(16, config.getCompileMemoryMb()) * 1024, FSIZE_KB);
        SandboxResult result = sandbox.exec(new SandboxCommand(buildDir,
                Commands.split(config.getCompileCommand()), null, limits, COMPILE_PROCESSES));

        if (!result.finished() || result.internalError()) {
            return JudgeOutcome.checkerError("the sandbox failed while compiling the checker");
        }
        if (result.timedOut()) {
            return JudgeOutcome.checkerError("the checker did not compile within the compile time limit");
        }
        if (result.exitCode() != 0) {
            String message = result.stderr().isBlank() ? result.stdout() : result.stderr();
            String truncated = truncate(message, properties.getMaxCompileOutputBytes());
            return JudgeOutcome.checkerError(truncated.isBlank() ? "the checker failed to compile" : truncated);
        }
        if (!Files.isRegularFile(buildDir.resolve(BINARY))) {
            return JudgeOutcome.checkerError("the checker compile command did not produce a file named " + BINARY);
        }
        return null;
    }

    @Override
    public Verdict verify(ProblemTestcase testCase, String contestantOutput, Path workDir) throws Exception {
        Path dir = SandboxFiles.createDir(workDir.resolve("check-" + testCase.getOrdinal()));
        SandboxFiles.copyArtifacts(buildDir, dir, config.getSourceFile(), true);
        SandboxFiles.writeFile(dir.resolve(INPUT_FILE), testCase.getInputData(), "rw-rw-rw-");
        SandboxFiles.writeFile(dir.resolve(OUTPUT_FILE), contestantOutput, "rw-rw-rw-");
        SandboxFiles.writeFile(dir.resolve(ANSWER_FILE), testCase.getOutputData(), "rw-rw-rw-");
        SandboxFiles.setPermissions(dir.resolve(BINARY), "rwxr-xr-x");

        int timeMs = Math.max(MIN_LIMIT_MS, config.getTimeLimitMs());
        Limits limits = new Limits(timeMs, (int) Math.ceil(timeMs / 1000.0) + WALL_EXTRA_SECONDS,
                Math.max(16, config.getMemoryLimitMb()) * 1024, FSIZE_KB);
        SandboxResult result = sandbox.exec(new SandboxCommand(dir, List.of(BINARY), null, limits, 1));

        if (!result.finished() || result.internalError()) {
            return Verdict.checkerError("the checker failed to start");
        }
        if (result.outOfMemory()) {
            return Verdict.checkerError("the checker exceeded the memory limit");
        }
        if (result.timedOut()) {
            return Verdict.checkerError("the checker exceeded the time limit");
        }
        if (result.signalled()) {
            return Verdict.checkerError(firstMessage(result.stderr(), "the checker crashed"));
        }
        return switch (result.exitCode()) {
            case 0 -> Verdict.accepted();
            case 1 -> Verdict.wrongAnswer(firstMessage(result.stdout(), null));
            case 2 -> Verdict.wrongAnswer(firstMessage(result.stdout(), "Presentation Error"));
            default -> Verdict.checkerError(
                    firstMessage(result.stderr(), "the checker exited with a non-zero exit code"));
        };
    }

    /**
     * Picks the first non-blank candidate and clamps it to the message column width.
     *
     * @param preferred text produced by the checker; {@code null} or blank falls back
     * @param fallback  message used when {@code preferred} is blank; may be {@code null}
     * @return the trimmed message, truncated to the configured cap, or {@code null} when both are blank
     */
    private String firstMessage(String preferred, String fallback) {
        String trimmed = preferred == null ? "" : preferred.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return truncate(trimmed, config.getMessageLimitBytes());
    }

    /**
     * Truncates free text so it fits the database column it is stored in.
     *
     * @param text  text to clamp; {@code null} becomes an empty string
     * @param limit maximum number of characters
     * @return the possibly truncated text, never {@code null}
     */
    private String truncate(String text, int limit) {
        String value = text == null ? "" : text;
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, Math.max(0, limit));
    }
}
