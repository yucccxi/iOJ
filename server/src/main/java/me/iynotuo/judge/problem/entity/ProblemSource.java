package me.iynotuo.judge.problem.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem source (contest, textbook, platform, ...). Maintained by administrators; problem editors
 * may only pick an existing row, never create one on the fly.
 */
@Data
public class ProblemSource {

    private Long id;

    private String name;

    private LocalDateTime createdAt;
}
