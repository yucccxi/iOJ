package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.Size;

/**
 * Uploads or clears the custom checker (special judge) of a problem.
 *
 * <p>An empty or blank code clears the checker, which restores the built-in
 * {@code OutputComparator}. The source is C++ compiled with {@code g++}; plain C code compiles too.</p>
 *
 * @param code checker source, or {@code null}/blank to remove it
 */
public record CheckerSaveRequest(

        @Size(max = 65_536, message = "判题器代码不能超过 64KB")
        String code
) {
}
