package me.iynotuo.judge.problem.entity;

import lombok.Data;

/**
 * 题目展示样例（题面中可见），与判题用的隐藏测试点分开存放。
 */
@Data
public class ProblemSample {

    private Long id;

    private Long problemId;

    /**
     * 从 1 开始的展示顺序。
     */
    private Integer ordinal;

    private String inputData;

    private String outputData;

    private String explanation;
}
