package me.iynotuo.judge.problem.mapper;

import lombok.Data;

/**
 * Tag row with usage counts: {@code localCount} comes from {@code problem_tags},
 * {@code externalCount} from {@code external_problem_tags}.
 */
@Data
public class TagUsageRow {

    private Long id;

    private String name;

    private long localCount;

    private long externalCount;
}
