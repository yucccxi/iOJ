package me.iynotuo.judge.protocol;

import java.util.List;

/**
 * One unit of work claimed by a judge agent.
 *
 * <p>Large test data is not required to travel in this payload: {@code testcaseBundleUrl} points at a
 * ZIP download that the agent can fetch with the same claim token. The archive contains a single
 * {@code testcases.json} entry. Small tasks may inline the test cases instead.</p>
 *
 * @param taskId             submission id used as the task id in the current implementation
 * @param submissionId       submission being judged
 * @param claimToken         fencing token that must be sent back with heartbeats and the result
 * @param leaseSeconds       how long the claim is valid without a heartbeat
 * @param language           language selected for the submission
 * @param code               source code
 * @param timeLimitMs        base time limit in milliseconds
 * @param memoryLimitMb      memory limit in megabytes
 * @param languageConfig     compile/run command snapshot
 * @param checkerCode        custom checker source, or {@code null} for the built-in comparator
 * @param testcases          inline test cases; empty when a bundle is used
 * @param testcaseBundleUrl  relative or absolute URL of a ZIP test case bundle containing
 *                           {@code testcases.json}
 * @param bundleSha256       SHA-256 of the bundle, or {@code null} when the server does not provide one
 */
public record JudgeTask(Long taskId,
                        Long submissionId,
                        String claimToken,
                        int leaseSeconds,
                        Language language,
                        String code,
                        int timeLimitMs,
                        int memoryLimitMb,
                        JudgeLanguageConfig languageConfig,
                        String checkerCode,
                        List<JudgeTestCase> testcases,
                        String testcaseBundleUrl,
                        String bundleSha256) {

    public JudgeTask {
        testcases = testcases == null ? List.of() : List.copyOf(testcases);
    }
}
