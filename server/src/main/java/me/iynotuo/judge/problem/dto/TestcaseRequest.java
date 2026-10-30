package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A single hidden test case. input and output hold the full text; one case should stay below 1 MB.
 */
public record TestcaseRequest(

        @NotNull(message = "测试点输入不能为 null")
        @Size(max = 1_048_576, message = "测试点输入不能超过 1MB")
        String input,

        @NotNull(message = "测试点输出不能为 null")
        @Size(max = 1_048_576, message = "测试点输出不能超过 1MB")
        String output
) {
}
