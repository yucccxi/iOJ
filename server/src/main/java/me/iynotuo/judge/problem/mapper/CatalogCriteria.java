package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;

import java.util.List;

/**
 * Query criteria for the unified catalog (local problems + external OJ links).
 *
 * <p>All filters are optional and combine with AND. {@code sourceId}, {@code rating} and
 * {@code tagId} apply to both branches: {@code tagId} resolves through {@code problem_tags} for
 * local rows and through {@code external_problem_tags} for external rows. The public {@code scope}
 * filter is translated into {@code includeLocal}/{@code includeExternal}/{@code externalSources}.</p>
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
     * Coarse difficulty labels ({@code EASY}/{@code MEDIUM}/{@code HARD}/{@code UNKNOWN});
     * multiple values are OR-ed together and apply to both kinds. Local rows use their own column,
     * external rows derive it from their rating.
     */
    private List<Difficulty> difficulties;

    /**
     * Whether the local problem branch participates in the UNION.
     */
    private boolean includeLocal = true;

    /**
     * Whether the external OJ branch participates in the UNION.
     */
    private boolean includeExternal = true;

    /**
     * External source names, lower-cased; null/empty means every external source.
     */
    private List<String> externalSources;

    private int offset;

    private int limit;
}
