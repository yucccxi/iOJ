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
     * Numeric difficulty rating (0-5000), used for filtering and sorting problem lists.
     * It is independent of {@link #difficulty}: difficulty stays as the coarse label shown to
     * clients, while rating carries the finer, comparable value (0-5000, e.g. Codeforces style).
     * May be null for problems that were never rated.
     */
    private Integer rating;

    /**
     * Problem statement (plain text or Markdown, rendered by the client).
     */
    private String description;

    private String inputFormat;

    private String outputFormat;

    private String hint;

    /**
     * Reference to {@code sources}; null when the problem has no recorded source.
     */
    private Long sourceId;

    /**
     * Source name joined in for responses (read-only, never written back).
     */
    private String sourceName;

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
