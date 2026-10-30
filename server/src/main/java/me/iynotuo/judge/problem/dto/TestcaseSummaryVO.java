package me.iynotuo.judge.problem.dto;

import lombok.Data;

/**
 * Test case metadata. The content itself is omitted so the list response stays small.
 */
@Data
public class TestcaseSummaryVO {

    private Long id;

    private Integer ordinal;

    private Integer inputBytes;

    private Integer outputBytes;

    private java.time.LocalDateTime createdAt;
}
