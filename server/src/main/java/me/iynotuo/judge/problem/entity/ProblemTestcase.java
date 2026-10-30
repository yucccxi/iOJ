package me.iynotuo.judge.problem.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * A hidden test case. Only the problem author and administrators can read its content.
 */
@Data
public class ProblemTestcase {

    private Long id;

    private Long problemId;

    /**
     * Test case number, starting at 1, shown as "Case #1".
     */
    private Integer ordinal;

    private String inputData;

    private String outputData;

    private LocalDateTime createdAt;
}
