package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Problem detail. solved tells whether the current user has already passed it (always false for anonymous callers).
 */
public record ProblemDetailVO(Long id, String title, Difficulty difficulty, String description, String inputFormat,
                              String outputFormat, String hint, String source, int timeLimitMs, int memoryLimitMb,
                              ProblemStatus status, Long authorId, String authorNickname, List<TagVO> tags,
                              List<SampleVO> samples, long submitCount, long acceptedCount, boolean solved,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
