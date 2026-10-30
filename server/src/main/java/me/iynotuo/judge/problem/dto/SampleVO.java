package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.problem.entity.ProblemSample;

public record SampleVO(int ordinal, String input, String output, String explanation) {

    public static SampleVO from(ProblemSample sample) {
        return new SampleVO(sample.getOrdinal(), sample.getInputData(),
                sample.getOutputData(), sample.getExplanation());
    }
}
