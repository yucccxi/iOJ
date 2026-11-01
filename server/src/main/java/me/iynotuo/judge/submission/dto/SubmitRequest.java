package me.iynotuo.judge.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.iynotuo.judge.common.enums.Language;

/**
 * A code submission. The length limit is not declared here: it comes from
 * {@code ioj.submission.max-code-length} and is enforced by the service, which reports
 * {@code CODE_TOO_LONG} with the configured value.
 */
public record SubmitRequest(

        @NotNull(message = "题目 id 不能为空")
        Long problemId,

        @NotNull(message = "语言不能为空")
        Language language,

        @NotBlank(message = "代码不能为空")
        String code
) {
}
