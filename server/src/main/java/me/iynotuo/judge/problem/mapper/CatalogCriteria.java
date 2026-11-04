package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;

import java.util.List;

/**
 * Query criteria for the unified catalog (local problems + external OJ links).
 *
 * <p>All filters are optional and combine with AND. {@code rating} applies to both branches.
 * {@code tagIds} resolves through {@code problem_tags} for local rows and through
 * {@code external_problem_tags} for external rows; {@code matchAllTags} selects AND instead of the
 * default OR. The public {@code scopeId} parameter is a list of scope ids and is applied to both
 * branches.</p>
 */
@Data
public class CatalogCriteria {

    private String keyword;

    /**
     * Tag ids to match; null/empty means no tag filter.
     */
    private List<Long> tagIds;

    /**
     * Number of distinct ids in {@link #tagIds}; used by the ALL match query.
     */
    private int tagCount;

    /**
     * {@code true} requires every tag in {@link #tagIds}; {@code false} requires at least one.
     */
    private boolean matchAllTags;

    /**
     * Exact rating match; {@code minRating}/{@code maxRating} define an inclusive range.
     */
    private Integer rating;

    private Integer minRating;

    private Integer maxRating;

    /**
     * Coarse difficulty labels ({@code EASY}/{@code MEDIUM}/{@code HARD}/{@code UNKNOWN});
     * multiple values are OR-ed together and apply to both kinds. Local rows use their own column,
     * external rows derive it from their rating.
     */
    private List<Difficulty> difficulties;

    /**
     * Scope ids to match; null/empty means every scope. Applied to both local and external rows.
     */
    private List<Long> scopeIds;

    /**
     * Sort key name: {@code DEFAULT}, {@code RATING}, {@code ACCEPTED}, {@code SUBMISSIONS} or
     * {@code ACCEPTANCE_RATE}.
     */
    private String sort = "DEFAULT";

    /**
     * {@code true} for ascending order, {@code false} for descending (the default).
     */
    private boolean sortAscending;

    private int offset;

    private int limit;
}
