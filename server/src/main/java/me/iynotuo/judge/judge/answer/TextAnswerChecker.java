package me.iynotuo.judge.judge.answer;

import me.iynotuo.judge.judge.OutputComparator;
import me.iynotuo.judge.problem.entity.ProblemTestcase;

import java.nio.file.Path;

/**
 * Default answer checker: compares the contestant output with the official answer using
 * {@link OutputComparator}, which ignores trailing blanks and blank lines at the end.
 */
public class TextAnswerChecker implements AnswerChecker {

    @Override
    public String name() {
        return "text";
    }

    @Override
    public Verdict verify(ProblemTestcase testCase, String contestantOutput, Path workDir) {
        if (OutputComparator.matches(testCase.getOutputData(), contestantOutput)) {
            return Verdict.accepted();
        }
        return Verdict.wrongAnswer(null);
    }
}
