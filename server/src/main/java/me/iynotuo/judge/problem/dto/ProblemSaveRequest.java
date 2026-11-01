package me.iynotuo.judge.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.iynotuo.judge.common.enums.Difficulty;

import java.util.List;

/**
 * Creates or fully replaces a problem.
 *
 * <p>Tags and sources are administrator-managed dictionaries: {@code tagIds} and {@code sourceId}
 * must reference existing rows and are never created implicitly, so a problem editor offers
 * selection only.</p>
 */
public record ProblemSaveRequest(

        @NotBlank(message = "题目标题不能为空")
        @Size(max = 128, message = "标题长度不能超过 128")
        String title,

        @NotNull(message = "难度不能为空")
        Difficulty difficulty,

        @Min(value = 0, message = "难度分不能小于 0")
        @Max(value = 5000, message = "难度分不能超过 5000")
        Integer rating,

        @NotBlank(message = "题面不能为空")
        @Size(max = 200_000, message = "题面过长")
        String description,

        @Size(max = 20_000, message = "输入格式说明过长")
        String inputFormat,

        @Size(max = 20_000, message = "输出格式说明过长")
        String outputFormat,

        @Size(max = 20_000, message = "提示过长")
        String hint,

        @Min(value = 1, message = "来源 id 必须为正整数")
        Long sourceId,

        @Min(value = 100, message = "时间限制至少 100ms")
        @Max(value = 30_000, message = "时间限制最大 30000ms")
        Integer timeLimitMs,

        @Min(value = 16, message = "内存限制至少 16MB")
        @Max(value = 1024, message = "内存限制最大 1024MB")
        Integer memoryLimitMb,

        @Size(max = 10, message = "标签最多 10 个")
        List<Long> tagIds,

        @Size(max = 10, message = "样例最多 10 个")
        List<@Valid SampleRequest> samples
) {
}
