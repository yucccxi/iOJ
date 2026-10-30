package me.iynotuo.judge.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.iynotuo.judge.common.enums.Difficulty;

import java.util.List;

/**
 * 创建 / 全量更新题目。tags 传名称即可，不存在的标签会自动创建。
 */
public record ProblemSaveRequest(

        @NotBlank(message = "题目标题不能为空")
        @Size(max = 128, message = "标题长度不能超过 128")
        String title,

        @NotNull(message = "难度不能为空")
        Difficulty difficulty,

        @NotBlank(message = "题面不能为空")
        @Size(max = 200_000, message = "题面过长")
        String description,

        @Size(max = 20_000, message = "输入格式说明过长")
        String inputFormat,

        @Size(max = 20_000, message = "输出格式说明过长")
        String outputFormat,

        @Size(max = 20_000, message = "提示过长")
        String hint,

        @Size(max = 128, message = "来源长度不能超过 128")
        String source,

        @Min(value = 100, message = "时间限制至少 100ms")
        @Max(value = 30_000, message = "时间限制最大 30000ms")
        Integer timeLimitMs,

        @Min(value = 16, message = "内存限制至少 16MB")
        @Max(value = 1024, message = "内存限制最大 1024MB")
        Integer memoryLimitMb,

        @Size(max = 10, message = "标签最多 10 个")
        List<@Pattern(regexp = "^[\\p{L}\\p{N}_+#.-]{1,32}$", message = "标签需为 1-32 位字母、数字或 _+#.-") String> tags,

        @Size(max = 10, message = "样例最多 10 个")
        List<@Valid SampleRequest> samples
) {
}
