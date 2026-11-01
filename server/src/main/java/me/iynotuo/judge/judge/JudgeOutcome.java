package me.iynotuo.judge.judge;

import me.iynotuo.judge.common.enums.SubmissionStatus;

import java.util.List;

/**
 * The result of judging one submission.
 *
 * @param status         overall status: the status of the first failing test case, or ACCEPTED
 * @param compileMessage compiler output, null unless the submission failed to compile
 * @param judgeMessage   system-level error message (for example missing test data); never contains test data
 * @param cases          per-test-case results (fail-fast: judging stops at the first failure)
 * @param timeUsedMs     slowest executed test case, in milliseconds
 * @param memoryUsedKb   peak memory; the local engine cannot measure it precisely, so it is usually null
 */
public record JudgeOutcome(SubmissionStatus status,
                           String compileMessage,
                           String judgeMessage,
                           List<CaseResult> cases,
                           Integer timeUsedMs,
                           Integer memoryUsedKb) {

    public record CaseResult(Integer ordinal, SubmissionStatus status, Integer timeUsedMs,
                             Integer memoryUsedKb, String message) {
    }

    public static JudgeOutcome systemError(String message) {
        return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, null, message, List.of(), null, null);
    }

    public static JudgeOutcome compilationError(String message) {
        return new JudgeOutcome(SubmissionStatus.COMPILATION_ERROR, message, null, List.of(), null, null);
    }

    /**
     * Builds the outcome of a run whose custom checker could not be used.
     *
     * <p>The message explains the checker failure to the problem author; it is stored in
     * {@code judge_message}, never in {@code compile_message}, because the contestant code compiled fine.</p>
     *
     * @param message explanation of the checker failure; never {@code null}
     * @return an outcome with status {@code CHECKER_ERROR} and no per-case results
     */
    public static JudgeOutcome checkerError(String message) {
        return new JudgeOutcome(SubmissionStatus.CHECKER_ERROR, null, message, List.of(), null, null);
    }
}
