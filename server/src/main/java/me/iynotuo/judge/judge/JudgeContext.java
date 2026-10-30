package me.iynotuo.judge.judge;

import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.problem.entity.Problem;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import me.iynotuo.judge.submission.entity.Submission;

import java.util.List;

/**
 * Everything needed to judge one submission.
 */
public record JudgeContext(Submission submission,
                           Problem problem,
                           List<ProblemTestcase> testcases,
                           JudgeProperties.LanguageConfig language) {
}
