package me.iynotuo.judge.protocol;

/**
 * Result of one test case.
 *
 * @param ordinal       test case number
 * @param status        result status; must be a terminal status
 * @param timeUsedMs    time used by this case in milliseconds, or {@code null}
 * @param memoryUsedKb  peak memory in kilobytes, or {@code null} when unavailable
 * @param message       optional failure explanation
 */
public record JudgeCaseResult(int ordinal,
                              SubmissionStatus status,
                              Integer timeUsedMs,
                              Integer memoryUsedKb,
                              String message) {
}
