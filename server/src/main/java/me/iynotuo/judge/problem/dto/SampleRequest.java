package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 题面展示样例。允许空字符串（例如只有输入的样例）。
 */
public record SampleRequest(

        @NotNull(message = "样例输入不能为 null")
        @Size(max = 10240, message = "样例输入过长")
        String input,

        @NotNull(message = "样例输出不能为 null")
        @Size(max = 10240, message = "样例输出过长")
        String output,

        @Size(max = 10240, message = "样例说明过长")
        String explanation
) {
}
