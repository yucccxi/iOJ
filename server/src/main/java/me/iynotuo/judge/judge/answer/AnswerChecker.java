package me.iynotuo.judge.judge.answer;

import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.judge.JudgeOutcome;
import me.iynotuo.judge.problem.entity.ProblemTestcase;

import java.nio.file.Path;

/**
 * Decides whether the answer of one test case is acceptable.
 *
 * <p>This is the policy layer of judging and it is engine agnostic: the built-in comparator needs
 * nothing but the two strings, while a custom checker compiles and runs author supplied code through a
 * {@link me.iynotuo.judge.judge.sandbox.Sandbox}. Which implementation is used is decided once per
 * submission by {@link AnswerCheckerFactory}, so judge engines never mention checkers at all.</p>
 */
public interface AnswerChecker {

    /**
     * Short name used in logs and messages.
     *
     * @return {@code "text"}, {@code "program"} or {@code "disabled"}; never {@code null}
     */
    String name();

    /**
     * Prepares the checker once per submission, for example by compiling its source.
     *
     * @param workDir temporary work directory of this submission
     * @return {@code null} when the checker is ready, otherwise a terminal outcome (for instance
     *         {@code CHECKER_ERROR} when the checker cannot be used or does not compile)
     * @throws Exception when preparation failed in an unexpected way; callers turn it into a system error
     */
    default JudgeOutcome prepare(Path workDir) throws Exception {
        return null;
    }

    /**
     * Verifies one test case.
     *
     * @param testCase          the test case, providing the official answer
     * @param contestantOutput  standard output of the contestant program, already truncated to the output cap
     * @param workDir           temporary work directory of this submission, used for per-case scratch files
     * @return the verdict of this case; never {@code null}
     * @throws Exception when the checker itself failed; callers turn it into {@code CHECKER_ERROR}
     */
    Verdict verify(ProblemTestcase testCase, String contestantOutput, Path workDir) throws Exception;

    /**
     * Result of checking one test case.
     *
     * @param status  {@code ACCEPTED}, {@code WRONG_ANSWER} or {@code CHECKER_ERROR}
     * @param message optional explanation shown to the submitter; may be {@code null}
     */
    record Verdict(SubmissionStatus status, String message) {

        /**
         * Convenience factory for an accepted case.
         *
         * @return an accepted verdict without a message
         */
        public static Verdict accepted() {
            return new Verdict(SubmissionStatus.ACCEPTED, null);
        }

        /**
         * Convenience factory for a rejected case.
         *
         * @param message optional explanation; may be {@code null}
         * @return a wrong-answer verdict
         */
        public static Verdict wrongAnswer(String message) {
            return new Verdict(SubmissionStatus.WRONG_ANSWER, message);
        }

        /**
         * Convenience factory for a broken checker, which is never the contestant's fault.
         *
         * @param message explanation stored with the case
         * @return a checker-error verdict
         */
        public static Verdict checkerError(String message) {
            return new Verdict(SubmissionStatus.CHECKER_ERROR, message);
        }
    }
}
