package me.iynotuo.judge.common.enums;

/**
 * Sort order for the unified catalog.
 */
public enum CatalogSort {
    /**
     * Local rows by {@code updated_at}, external rows by clist {@code time}; newest first.
     */
    DEFAULT,

    /**
     * Numeric difficulty ({@code rating}); highest first, unrated treated as 0.
     */
    RATING,

    /**
     * Accepted count; highest first.
     */
    ACCEPTED,

    /**
     * Submission count; highest first.
     */
    SUBMISSIONS,

    /**
     * Acceptance rate ({@code accepted_count / submit_count}); highest first.
     * Rows without submissions are placed last.
     */
    ACCEPTANCE_RATE
}
