package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;

/**
 * Database row for a problem list (author nickname joined in to avoid N+1 queries).
 */
@Data
public class ProblemSummaryRow {

    private Long id;

    private String title;

    private Difficulty difficulty;

    /**
     * Numeric rating (0-5000); null when the problem has never been rated.
     */
    private Integer rating;

    private ProblemStatus status;

    private long submitCount;

    private long acceptedCount;

    private Long authorId;

    private String authorNickname;

    private LocalDateTime createdAt;
}
