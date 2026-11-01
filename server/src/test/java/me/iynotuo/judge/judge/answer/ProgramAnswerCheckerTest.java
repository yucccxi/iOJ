package me.iynotuo.judge.judge.answer;

import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.judge.JudgeOutcome;
import me.iynotuo.judge.judge.sandbox.Sandbox;
import me.iynotuo.judge.judge.sandbox.SandboxCommand;
import me.iynotuo.judge.judge.sandbox.SandboxResult;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how a custom checker's result is turned into a submission verdict.
 *
 * <p>A fake sandbox feeds canned results, so the mapping is covered without a real isolate installation.
 * This is exactly the layer that used to have no tests at all.</p>
 */
class ProgramAnswerCheckerTest {

    /**
     * Accepts a case when the checker exits with 0.
     */
    @Test
    void exitZeroAccepts(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        ProgramAnswerChecker checker = prepared(sandbox, work);

        sandbox.enqueue(result(true, "OK", 0, false, "", ""));

        assertThat(checker.verify(caseOne(), "3\n", work).status()).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    /**
     * Reports a wrong answer when the checker exits with 1, carrying its message.
     *
     * <p>Isolate labels a non-zero exit as {@code RE}; treating that as a crash used to make every special
     * judge case fail with {@code CHECKER_ERROR}, so this test pins the distinction.</p>
     */
    @Test
    void exitOneIsWrongAnswerEvenThoughIsolateReportsRe(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        ProgramAnswerChecker checker = prepared(sandbox, work);

        sandbox.enqueue(result(true, "RE", 1, false, "expected 3", ""));

        AnswerChecker.Verdict verdict = checker.verify(caseOne(), "4\n", work);
        assertThat(verdict.status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(verdict.message()).isEqualTo("expected 3");
    }

    /**
     * Reports a presentation error as a wrong answer with a default message.
     */
    @Test
    void exitTwoIsWrongAnswerWithPresentationMessage(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        ProgramAnswerChecker checker = prepared(sandbox, work);

        sandbox.enqueue(result(true, "RE", 2, false, "", ""));

        AnswerChecker.Verdict verdict = checker.verify(caseOne(), "3\n", work);
        assertThat(verdict.status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(verdict.message()).isEqualTo("Presentation Error");
    }

    /**
     * Reports an unexpected exit code as a broken checker, never as a contestant failure.
     */
    @Test
    void unexpectedExitCodeIsACheckerError(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        ProgramAnswerChecker checker = prepared(sandbox, work);

        sandbox.enqueue(result(true, "RE", 7, false, "", "internal failure"));

        AnswerChecker.Verdict verdict = checker.verify(caseOne(), "3\n", work);
        assertThat(verdict.status()).isEqualTo(SubmissionStatus.CHECKER_ERROR);
        assertThat(verdict.message()).isEqualTo("internal failure");
    }

    /**
     * Reports a signal kill as a broken checker.
     */
    @Test
    void signalIsACheckerError(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        ProgramAnswerChecker checker = prepared(sandbox, work);

        sandbox.enqueue(result(true, "SG", 0, false, "", "segmentation fault"));

        AnswerChecker.Verdict verdict = checker.verify(caseOne(), "3\n", work);
        assertThat(verdict.status()).isEqualTo(SubmissionStatus.CHECKER_ERROR);
        assertThat(verdict.message()).isEqualTo("segmentation fault");
    }

    /**
     * Reports a checker that runs out of its own limits as a broken checker, never as a contestant TLE.
     */
    @Test
    void checkerTimeoutIsACheckerError(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        ProgramAnswerChecker checker = prepared(sandbox, work);

        sandbox.enqueue(result(true, "TO", -1, false, "", ""));

        AnswerChecker.Verdict verdict = checker.verify(caseOne(), "3\n", work);
        assertThat(verdict.status()).isEqualTo(SubmissionStatus.CHECKER_ERROR);
        assertThat(verdict.message()).contains("time limit");
    }

    /**
     * Reports a checker that is killed by the cgroup memory limit as a broken checker.
     */
    @Test
    void checkerOutOfMemoryIsACheckerError(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        ProgramAnswerChecker checker = prepared(sandbox, work);

        sandbox.enqueue(result(true, "OK", 0, true, "", ""));

        AnswerChecker.Verdict verdict = checker.verify(caseOne(), "3\n", work);
        assertThat(verdict.status()).isEqualTo(SubmissionStatus.CHECKER_ERROR);
        assertThat(verdict.message()).contains("memory limit");
    }

    /**
     * Surfaces the compiler output when the checker does not compile.
     */
    @Test
    void compileFailureIsReportedWithTheCompilerOutput(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        sandbox.setProduceBinary(false);
        ProgramAnswerChecker checker = new ProgramAnswerChecker(sandbox, properties(), "int main( {");
        sandbox.enqueue(result(true, "RE", 1, false, "", "checker.cpp:1:11: error: expected ')'"));

        JudgeOutcome outcome = checker.prepare(work);

        assertThat(outcome).isNotNull();
        assertThat(outcome.status()).isEqualTo(SubmissionStatus.CHECKER_ERROR);
        assertThat(outcome.judgeMessage()).contains("expected ')'");
    }

    /**
     * Surfaces a missing checker binary, which means the compile command was changed carelessly.
     */
    @Test
    void missingBinaryIsReported(@TempDir Path work) throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        sandbox.setProduceBinary(false);
        ProgramAnswerChecker checker = new ProgramAnswerChecker(sandbox, properties(), "int main() { return 0; }");
        sandbox.enqueue(result(true, "OK", 0, false, "", ""));

        JudgeOutcome outcome = checker.prepare(work);

        assertThat(outcome).isNotNull();
        assertThat(outcome.status()).isEqualTo(SubmissionStatus.CHECKER_ERROR);
        assertThat(outcome.judgeMessage()).contains("did not produce");
    }

    /**
     * Builds a checker that already compiled successfully.
     *
     * @param sandbox fake sandbox that will answer the compile step
     * @param work    temporary work directory of the submission
     * @return a prepared checker
     * @throws Exception when preparation fails
     */
    private ProgramAnswerChecker prepared(FakeSandbox sandbox, Path work) throws Exception {
        ProgramAnswerChecker checker = new ProgramAnswerChecker(sandbox, properties(), "int main() { return 0; }");
        sandbox.enqueue(result(true, "OK", 0, false, "", ""));
        assertThat(checker.prepare(work)).isNull();
        return checker;
    }

    /**
     * Creates judge properties with the default checker configuration.
     *
     * @return judge properties
     */
    private JudgeProperties properties() {
        return new JudgeProperties();
    }

    /**
     * Builds the single test case used by the assertions.
     *
     * @return a test case with ordinal 1
     */
    private ProblemTestcase caseOne() {
        ProblemTestcase testcase = new ProblemTestcase();
        testcase.setOrdinal(1);
        testcase.setInputData("1 2\n");
        testcase.setOutputData("3\n");
        return testcase;
    }

    /**
     * Builds a canned sandbox result.
     *
     * @param finished   whether the command ended on its own
     * @param status     sandbox status
     * @param exitCode   exit code
     * @param outOfMemory whether the sandbox reported a memory kill
     * @param stdout     captured standard output
     * @param stderr     captured standard error
     * @return the result
     */
    private SandboxResult result(boolean finished, String status, int exitCode, boolean outOfMemory,
                                 String stdout, String stderr) {
        return new SandboxResult(finished, status, exitCode, 0, 10, 1_024, outOfMemory, stdout, stderr, false);
    }

    /**
     * Sandbox that returns canned results and, by default, pretends the compiler produced the checker binary.
     */
    private static final class FakeSandbox implements Sandbox {

        private final Deque<SandboxResult> queued = new ArrayDeque<>();
        private boolean produceBinary = true;

        void enqueue(SandboxResult result) {
            queued.add(result);
        }

        void setProduceBinary(boolean produceBinary) {
            this.produceBinary = produceBinary;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public boolean supportsAuthorCode() {
            return true;
        }

        @Override
        public SandboxResult exec(SandboxCommand command) throws Exception {
            if (produceBinary) {
                Files.writeString(command.directory().resolve("checker"), "");
            }
            SandboxResult next = queued.poll();
            return next == null ? new SandboxResult(true, "OK", 0, 0, 1, 0, false, "", "", false) : next;
        }
    }
}
