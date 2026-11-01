package me.iynotuo.judge.judge;

import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.problem.entity.Problem;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import me.iynotuo.judge.submission.entity.Submission;

import java.util.List;

/**
 * Everything needed to judge one submission.
 *
 * @param submission  the submission being judged
 * @param problem     the problem, providing the limits
 * @param testcases   the ordered test cases
 * @param language    the resolved language configuration
 * @param checkerCode custom checker source of the problem, or {@code null} when the problem uses the
 *                    built-in {@link OutputComparator}
 */
public record JudgeContext(Submission submission,
                           Problem problem,
                           List<ProblemTestcase> testcases,
                           JudgeProperties.LanguageConfig language,
                           String checkerCode) {

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
