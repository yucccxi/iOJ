package me.iynotuo.judge.problem.dto;

/**
 * The custom checker source of one problem, returned to the author and to administrators only.
 *
 * @param code checker source, or an empty string when the problem uses the built-in comparator
 */
public record CheckerDetailVO(String code) {
}
