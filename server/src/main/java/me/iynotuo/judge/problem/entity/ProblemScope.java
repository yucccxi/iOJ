package me.iynotuo.judge.problem.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem scope (external OJ platform, contest, ...). External scopes are maintained by
 * administrators. {@link #LOCAL} is a reserved system row referenced by every locally authored
 * problem; it cannot be renamed or deleted.
 */
@Data
public class ProblemScope {

    /**
     * Name of the reserved scope assigned to local problems automatically.
     */
    public static final String LOCAL = "LOCAL";

    private Long id;

    private String name;

    private LocalDateTime createdAt;
}
