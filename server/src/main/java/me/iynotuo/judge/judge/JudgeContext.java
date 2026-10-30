package me.iynotuo.judge.judge;

import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.problem.entity.Problem;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import me.iynotuo.judge.submission.entity.Submission;

import java.util.List;

/**
 * 一次判题所需的全部输入。
 */
public record JudgeContext(Submission submission,
                           Problem problem,
                           List<ProblemTestcase> testcases,
                           JudgeProperties.LanguageConfig language) {
}
