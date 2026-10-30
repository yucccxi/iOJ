package me.iynotuo.judge.problem.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;

/**
 * 题目表映射。
 */
@Data
public class Problem {

    private Long id;

    private String title;

    private Difficulty difficulty;

    /**
     * 题面（纯文本或 Markdown，由前端渲染）。
     */
    private String description;

    private String inputFormat;

    private String outputFormat;

    private String hint;

    /**
     * 题目来源，如 "2025 校赛 A"。
     */
    private String source;

    private Integer timeLimitMs;

    private Integer memoryLimitMb;

    private Long authorId;

    private ProblemStatus status;

    private Integer submitCount;

    private Integer acceptedCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
