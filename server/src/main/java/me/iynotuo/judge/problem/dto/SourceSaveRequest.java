package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates or renames a problem source (administrators only).
 */
public record SourceSaveRequest(

        @NotBlank(message = "来源名不能为空")
        @Size(max = 128, message = "来源名长度不能超过 128")
        String name
) {
}
