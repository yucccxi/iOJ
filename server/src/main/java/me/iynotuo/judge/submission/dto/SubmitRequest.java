package me.iynotuo.judge.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.iynotuo.judge.common.enums.Language;

public record SubmitRequest(

        @NotNull(message = "题目 id 不能为空")
        Long problemId,

        @NotNull(message = "语言不能为空")
        Language language,

        @NotBlank(message = "代码不能为空")
        @Size(max = 65_536, message = "代码长度不能超过 65536 字符")
        String code
) {
}
