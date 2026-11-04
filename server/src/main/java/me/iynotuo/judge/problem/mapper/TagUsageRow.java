package me.iynotuo.judge.problem.mapper;

import lombok.Data;

/**
 * Tag row with usage counts: {@code localCount} counts published problems linked through
 * {@code problem_tags}, {@code externalCount} counts {@code external_problem_tags}.
 */
@Data
public class TagUsageRow {

    private Long id;

    private String name;

    private long localCount;

    private long externalCount;
}
