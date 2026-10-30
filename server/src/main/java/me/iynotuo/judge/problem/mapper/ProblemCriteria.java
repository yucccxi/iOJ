package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.util.List;

/**
 * 题目列表查询条件（MyBatis 动态 SQL 参数对象）。
 */
@Data
public class ProblemCriteria {

    private String keyword;

    private Difficulty difficulty;

    private Long tagId;

    private Long authorId;

    /**
     * 为空表示不限状态；公开列表固定传 [PUBLISHED]。
     */
    private List<ProblemStatus> statuses;

    private int offset;

    private int limit;
}
