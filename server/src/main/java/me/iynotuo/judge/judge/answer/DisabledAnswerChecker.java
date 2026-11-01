package me.iynotuo.judge.judge.answer;

import me.iynotuo.judge.judge.JudgeOutcome;
import me.iynotuo.judge.problem.entity.ProblemTestcase;

import java.nio.file.Path;

/**
 * Checker used when the problem carries custom checker source but this deployment cannot run it.
 *
 * <p>It reports {@code CHECKER_ERROR} instead of silently falling back to the text comparator: a silent
 * fallback would change how an existing problem is judged, and a wrong verdict is much harder to notice
 * than an explicit failure.</p>
 */
public class DisabledAnswerChecker implements AnswerChecker {

    private final String reason;

    /**
     * Creates the checker.
     *
     * @param reason human-readable explanation stored in {@code judge_message}; never {@code null}
     */
    public DisabledAnswerChecker(String reason) {
        this.reason = reason;
    }

    @Override
    public String name() {
        return "disabled";
    }

    @Override
    public JudgeOutcome prepare(Path workDir) {
        return JudgeOutcome.checkerError(reason);
    }

    @Override
    public Verdict verify(ProblemTestcase testCase, String contestantOutput, Path workDir) {
        return Verdict.checkerError(reason);
    }
}
