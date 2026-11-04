package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A problem list item. {@code rating} is the numeric difficulty used for filtering/sorting;
 * an unrated problem is exposed as {@code -1} rather than {@code null} so clients never have to
 * special-case it.
 */
public record ProblemSummaryVO(Long id, String title, Difficulty difficulty, Integer rating,
                               ProblemStatus status,
                               long submitCount, long acceptedCount, Long authorId, String authorNickname,
                               List<TagVO> tags, LocalDateTime createdAt) {

    public ProblemSummaryVO {
        rating = rating == null ? -1 : rating;
    }
}
