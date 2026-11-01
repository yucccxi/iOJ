package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Problem detail. solved tells whether the current user has already passed it (always false for anonymous callers).
 *
 * <p>hasChecker only reports whether a custom checker exists; the checker source itself is never
 * part of this response.</p>
 *
 * <p>rating is the numeric difficulty (0-5000) used for filtering; it may be null when the problem
 * has never been rated. difficulty remains the coarse label and is not derived from rating.</p>
 */
public record ProblemDetailVO(Long id, String title, Difficulty difficulty, Integer rating, String description,
                              String inputFormat, String outputFormat, String hint,
                              Long sourceId, String sourceName,
                              int timeLimitMs, int memoryLimitMb,
                              ProblemStatus status, Long authorId, String authorNickname, List<TagVO> tags,
                              List<SampleVO> samples, long submitCount, long acceptedCount, boolean solved,
                              LocalDateTime createdAt, LocalDateTime updatedAt, boolean hasChecker) {
}
