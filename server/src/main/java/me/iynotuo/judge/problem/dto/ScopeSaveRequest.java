package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates or renames a problem scope (administrators only).
 */
public record ScopeSaveRequest(

        @NotBlank(message = "scope 名不能为空")
        @Size(max = 128, message = "scope 名长度不能超过 128")
        String name
) {
}
