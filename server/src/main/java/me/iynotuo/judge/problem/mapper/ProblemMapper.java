package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.common.enums.ProblemStatus;
import me.iynotuo.judge.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for problems.
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

    /**
     * Reads the custom checker source of one problem.
     *
     * <p>Kept separate from {@link #findById} on purpose: the public problem detail path uses
     * {@code findById}, and the checker source must never end up in a response visible to
     * contestants.</p>
     *
     * @param problemId problem id
     * @return the checker source, or {@code null} when the problem uses the built-in comparator
     */
    String findCheckerCode(@Param("problemId") Long problemId);

    /**
     * Replaces or clears the custom checker source.
     *
     * @param id          problem id
     * @param checkerCode checker source, or {@code null} to fall back to the built-in comparator
     * @param updatedAt   timestamp written to {@code updated_at}
     * @return the number of affected rows
     */
    int updateChecker(@Param("id") Long id,
                      @Param("checkerCode") String checkerCode,
                      @Param("updatedAt") LocalDateTime updatedAt);
}
