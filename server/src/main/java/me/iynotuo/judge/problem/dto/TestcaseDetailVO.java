package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.problem.entity.ProblemTestcase;

public record TestcaseDetailVO(Long id, Integer ordinal, String input, String output) {

    public static TestcaseDetailVO from(ProblemTestcase testcase) {
        return new TestcaseDetailVO(testcase.getId(), testcase.getOrdinal(),
                testcase.getInputData(), testcase.getOutputData());
    }
}
