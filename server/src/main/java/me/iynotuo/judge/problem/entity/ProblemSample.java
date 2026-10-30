package me.iynotuo.judge.problem.entity;

import lombok.Data;

/**
 * A sample shown on the problem page. Kept separate from the hidden test cases used for judging.
 */
@Data
public class ProblemSample {

    private Long id;

    private Long problemId;

    /**
     * Display order, starting at 1.
     */
    private Integer ordinal;

    private String inputData;

    private String outputData;

    private String explanation;
}
