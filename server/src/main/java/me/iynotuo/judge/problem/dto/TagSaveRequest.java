package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Creates or renames a tag (administrators only). The pattern matches the limits previously enforced
 * on problem tag names: 1-32 letters, digits, CJK characters or {@code _+#.-}.
 */
public record TagSaveRequest(

        @NotBlank(message = "标签名不能为空")
        @Pattern(regexp = "^[\\p{L}\\p{N}_+#.-]{1,32}$", message = "标签需为 1-32 位字母、数字、中文或 _+#.-")
        String name
) {
}
