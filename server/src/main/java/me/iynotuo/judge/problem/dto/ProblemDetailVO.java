package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目详情。solved 表示当前登录用户是否已通过（匿名访问时为 false）。
 */
public record ProblemDetailVO(Long id, String title, Difficulty difficulty, String description, String inputFormat,
                              String outputFormat, String hint, String source, int timeLimitMs, int memoryLimitMb,
                              ProblemStatus status, Long authorId, String authorNickname, List<TagVO> tags,
                              List<SampleVO> samples, long submitCount, long acceptedCount, boolean solved,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
