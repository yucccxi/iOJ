package me.iynotuo.judge.common.enums;

/**
 * Problem difficulty, used for filtering problem lists.
 *
 * <p>{@code UNKNOWN} is only produced for external OJ problems whose source rating is missing;
 * locally authored problems must use {@code EASY}, {@code MEDIUM} or {@code HARD}.</p>
 */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD,
    UNKNOWN
}
