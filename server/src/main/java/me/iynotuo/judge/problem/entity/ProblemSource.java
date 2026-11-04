package me.iynotuo.judge.problem.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem source (contest, textbook, platform, ...). External OJ sources are maintained by
 * administrators. {@link #LOCAL} is a reserved system row referenced by every locally authored
 * problem; it cannot be renamed or deleted.
 */
@Data
public class ProblemSource {

    /**
     * Name of the reserved source assigned to local problems automatically.
     */
    public static final String LOCAL = "LOCAL";

    private Long id;

    private String name;

    private LocalDateTime createdAt;
}
