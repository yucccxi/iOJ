package me.iynotuo.judge.problem.mapper;

import lombok.Data;

/**
 * 题目-标签关联行，用于批量查询后按 problemId 分组。
 */
@Data
public class ProblemTagRow {

    private Long problemId;

    private Long id;

    private String name;
}
