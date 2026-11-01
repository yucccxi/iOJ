package me.iynotuo.judge.problem.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;

/**
 * Problem table mapping.
 */
@Data
public class Problem {

    private Long id;

    private String title;

    private Difficulty difficulty;

    /**
     * Problem statement (plain text or Markdown, rendered by the client).
     */
    private String description;

    private String inputFormat;

    private String outputFormat;

    private String hint;

    /**
     * Problem source, for example "2025 Campus Contest A".
     */
    private String source;

    /**
     * Whether the problem ships a custom checker. This is a computed, read-only flag: the checker
     * source itself is never loaded into this entity, so it cannot leak through a detail response.
     */
    private boolean hasChecker;

    private Integer timeLimitMs;

    private Integer memoryLimitMb;

    private Long authorId;

    private ProblemStatus status;

    private Integer submitCount;

    private Integer acceptedCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
