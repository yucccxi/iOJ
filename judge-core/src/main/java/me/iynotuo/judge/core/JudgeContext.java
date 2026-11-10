package me.iynotuo.judge.core;

import me.iynotuo.judge.core.config.JudgeProperties;

import java.util.List;

/**
 * Everything needed to judge one submission.
 *
 * <p>The context deliberately uses judge-local models instead of MyBatis entities, so {@code judge-core}
 * can be reused by a remote judge agent without depending on the server's database layer.</p>
 *
 * @param submission  the submission being judged
 * @param problem     the problem, providing the limits
 * @param testcases   the ordered test cases
 * @param language    the resolved language configuration
 * @param checkerCode custom checker source of the problem, or {@code null} when the problem uses the
 *                    built-in {@link OutputComparator}
 */
public record JudgeContext(JudgeSubmission submission,
                           JudgeProblem problem,
                           List<JudgeTestcase> testcases,
                           JudgeProperties.LanguageConfig language,
                           String checkerCode) {

    public JudgeContext {
        testcases = testcases == null ? List.of() : List.copyOf(testcases);
    }

    /**
     * Tells whether a custom checker has to be compiled and run for this submission.
     *
     * @return {@code true} when the problem carries non-blank checker source; {@code false} when the
     *         answer should be compared with {@link OutputComparator}
     */
    public boolean hasCustomChecker() {
        return checkerCode != null && !checkerCode.isBlank();
    }
}
