package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A single hidden test case. input and output hold the full text of one case.
 *
 * <p>The size limit is deliberately not declared here: it comes from
 * {@code ioj.problem.max-testcase-bytes} and is enforced by the service, so the limit can be
 * raised or lowered in configuration without touching code. Keeping a second hard-coded
 * {@code @Size} here would silently win over the configured value.</p>
 */
public record TestcaseRequest(

        @NotNull(message = "测试点输入不能为 null")
        String input,

        @NotNull(message = "测试点输出不能为 null")
        String output
) {
}
