package me.iynotuo.judge.submission.mapper;

import me.iynotuo.judge.submission.entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Data access for submissions. The judge worker claims work through lockNextPendingId().
 */
@Mapper
public interface SubmissionMapper {

    int insert(Submission submission);

    Submission findById(@Param("id") Long id);

    List<SubmissionSummaryRow> findPage(SubmissionCriteria criteria);

    long countPage(SubmissionCriteria criteria);

    int countAccepted(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /**
     * Locks the oldest pending submission (row lock in a short transaction).
     */
    Long lockNextPendingId();

    int markJudging(@Param("id") Long id);

    int updateResult(Submission submission);

    /**
     * Resets leftover JUDGING submissions to PENDING when the service restarts.
     */
    int resetJudgingToPending();
}
