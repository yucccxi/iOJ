package me.iynotuo.judge.core.answer;

import me.iynotuo.judge.core.OutputComparator;
import me.iynotuo.judge.core.JudgeTestcase;

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
    public Verdict verify(JudgeTestcase testCase, String contestantOutput, Path workDir) {
        if (OutputComparator.matches(testCase.output(), contestantOutput)) {
            return Verdict.accepted();
        }
        return Verdict.wrongAnswer(null);
    }
}
