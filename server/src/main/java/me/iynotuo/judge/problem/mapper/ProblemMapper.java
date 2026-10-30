package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.common.enums.ProblemStatus;
import me.iynotuo.judge.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目表访问层。
 */
@Mapper
public interface ProblemMapper {

    int insert(Problem problem);

    int update(Problem problem);

    int deleteById(@Param("id") Long id);

    Problem findById(@Param("id") Long id);

    List<ProblemSummaryRow> findPage(ProblemCriteria criteria);

    long countPage(ProblemCriteria criteria);

    int increaseSubmitCount(@Param("id") Long id);

    int increaseAcceptedCount(@Param("id") Long id);

    int updateStatus(@Param("id") Long id,
                     @Param("status") ProblemStatus status,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
