package me.iynotuo.judge.protocol;

import java.util.List;

/**
 * Final result reported by a judge agent.
 *
 * <p>The server accepts it only when {@code claimToken} still matches the current claim. A repeated
 * report with the same token is idempotent; a token from an expired or requeued claim is rejected.</p>
 *
 * @param claimToken     fencing token returned by the claim request
 * @param status         overall terminal status
 * @param timeUsedMs     slowest executed test case in milliseconds, or {@code null}
 * @param memoryUsedKb   peak memory in kilobytes, or {@code null}
 * @param compileMessage compiler output, or {@code null}
 * @param judgeMessage   system/checker error explanation, or {@code null}
 * @param cases          per-case results, possibly empty
 */
public record JudgeResult(String claimToken,
                          SubmissionStatus status,
                          Integer timeUsedMs,
                          Integer memoryUsedKb,
                          String compileMessage,
                          String judgeMessage,
                          List<JudgeCaseResult> cases) {

    public JudgeResult {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
