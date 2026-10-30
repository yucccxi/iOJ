package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.time.LocalDateTime;

/**
 * 题目列表的数据库行（已 join 作者昵称，避免 N+1）。
 */
@Data
public class ProblemSummaryRow {

    private Long id;

    private String title;

    private Difficulty difficulty;

    private ProblemStatus status;

    private long submitCount;

    private long acceptedCount;

    private Long authorId;

    private String authorNickname;

    private LocalDateTime createdAt;
}
