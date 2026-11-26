package me.iynotuo.judge.protocol;

/**
 * One unit of work claimed by a judge agent.
 *
 * <p>Test data always travels out of band as a ZIP bundle that the agent downloads from
 * {@code /internal/judge/tasks/{taskId}/testcases} with the same claim token. The archive contains
 * a single {@code testcases.json} entry.</p>
 *
 * @param taskId             submission id used as the task id in the current implementation
 * @param submissionId       submission being judged
 * @param problemId          problem whose test data is used; database id
 * @param claimToken         fencing token that must be sent back with heartbeats and the result
 * @param leaseSeconds       how long the claim is valid without a heartbeat
 * @param language           language selected for the submission
 * @param code               source code
 * @param timeLimitMs        base time limit in milliseconds
 * @param memoryLimitMb      memory limit in megabytes
 * @param languageConfig     compile/run command snapshot
 * @param checkerCode        custom checker source, or {@code null} for the built-in comparator
 * @param bundleSha256       SHA-256 of the ZIP test case bundle
 */
public record JudgeTask(Long taskId,
                        Long submissionId,
                        Long problemId,
                        String claimToken,
                        int leaseSeconds,
                        Language language,
                        String code,
                        int timeLimitMs,
                        int memoryLimitMb,
                        JudgeLanguageConfig languageConfig,
                        String checkerCode,
                        String bundleSha256) {
}
