package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A problem list item. {@code rating} is the numeric difficulty used for filtering/sorting;
 * it may be null when the problem has never been rated.
 */
public record ProblemSummaryVO(Long id, String title, Difficulty difficulty, Integer rating,
                               ProblemStatus status,
                               long submitCount, long acceptedCount, Long authorId, String authorNickname,
                               List<TagVO> tags, LocalDateTime createdAt) {
}
