package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.ProblemStatus;

import java.util.List;

/**
 * Query criteria for problem lists (a MyBatis dynamic SQL parameter object).
 */
@Data
public class ProblemCriteria {

    private String keyword;

    private Long authorId;

    /**
     * Empty means no status filter; the review list optionally narrows by one status.
     */
    private List<ProblemStatus> statuses;

    private int offset;

    private int limit;
}
