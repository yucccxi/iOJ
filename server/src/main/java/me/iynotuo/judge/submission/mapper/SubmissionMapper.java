package me.iynotuo.judge.submission.mapper;

import me.iynotuo.judge.submission.entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提交表访问层。判题 worker 通过 {@link #lockNextPendingId()} 抢占任务。
 */
@Mapper
public interface SubmissionMapper {

    int insert(Submission submission);

    Submission findById(@Param("id") Long id);

    List<SubmissionSummaryRow> findPage(SubmissionCriteria criteria);

    long countPage(SubmissionCriteria criteria);

    int countAccepted(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /**
     * 抢占最早的一条待评测提交（行锁 + 短事务）。
     */
    Long lockNextPendingId();

    int markJudging(@Param("id") Long id);

    int updateResult(Submission submission);

    /**
     * 服务重启时把残留的 JUDGING 重置为 PENDING。
     */
    int resetJudgingToPending();
}
