package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;

/**
 * Query criteria for the unified catalog (local problems + external OJ links).
 *
 * <p>All filters are optional and combine with AND. {@code sourceId}, {@code rating} and
 * {@code tagId} apply to both branches: {@code tagId} resolves through {@code problem_tags} for
 * local rows and through {@code external_problem_tags} for external rows.</p>
 */
@Data
public class CatalogCriteria {

    private String keyword;

    private Long sourceId;

    private Long tagId;

    /**
     * Exact rating match; {@code minRating}/{@code maxRating} define an inclusive range.
     */
    private Integer rating;

    private Integer minRating;

    private Integer maxRating;

    /**
     * Coarse difficulty label ({@code EASY}/{@code MEDIUM}/{@code HARD}); applies to LOCAL rows only.
     */
    private Difficulty difficulty;

    /**
     * {@code LOCAL} or {@code EXTERNAL}; null means both.
     */
    private String kind;

    private int offset;

    private int limit;
}
