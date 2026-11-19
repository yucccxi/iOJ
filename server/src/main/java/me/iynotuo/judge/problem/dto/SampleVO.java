package me.iynotuo.judge.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.iynotuo.judge.problem.entity.ProblemSample;

public record SampleVO(int ordinal, String input, String output,
                       @Schema(nullable = true) String explanation) {

    public static SampleVO from(ProblemSample sample) {
        return new SampleVO(sample.getOrdinal(), sample.getInputData(),
                sample.getOutputData(), sample.getExplanation());
    }
}
