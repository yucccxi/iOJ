package me.iynotuo.judge.problem.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 隐藏测试点。只有题目作者/管理员可以查看内容。
 */
@Data
public class ProblemTestcase {

    private Long id;

    private Long problemId;

    /**
     * 从 1 开始的测试点编号，用于展示 "Case #1"。
     */
    private Integer ordinal;

    private String inputData;

    private String outputData;

    private LocalDateTime createdAt;
}
