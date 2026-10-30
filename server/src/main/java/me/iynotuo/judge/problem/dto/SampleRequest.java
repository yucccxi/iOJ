package me.iynotuo.judge.problem.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A sample shown on the problem page. Empty strings are allowed (for example a sample with input only).
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
