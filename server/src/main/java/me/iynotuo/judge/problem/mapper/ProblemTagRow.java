package me.iynotuo.judge.problem.mapper;

import lombok.Data;

/**
 * Problem-tag row, grouped by problem id after a batch query.
 */
@Data
public class ProblemTagRow {

    private Long problemId;

    private Long id;

    private String name;
}
