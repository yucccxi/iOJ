package me.iynotuo.judge.problem.mapper;

import lombok.Data;

/**
 * Tag row joined for an external problem, used to batch-load tags without N+1 queries.
 */
@Data
public class ExternalProblemTagRow {

    private Long externalProblemId;

    private Long id;

    private String name;
}
