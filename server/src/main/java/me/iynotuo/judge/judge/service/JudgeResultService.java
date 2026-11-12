package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.problem.mapper.ProblemMapper;
import me.iynotuo.judge.protocol.JudgeCaseResult;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.SubmissionStatus;
import me.iynotuo.judge.submission.entity.Submission;
import me.iynotuo.judge.submission.entity.SubmissionCase;
import me.iynotuo.judge.submission.mapper.SubmissionMapper;
import me.iynotuo.judge.submission.service.SubmissionCaseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies results reported by remote judge agents.
 *
 * <p>The write is fenced by the claim token and the JUDGING state, so a late result from an expired
 * lease, a requeued task or an administrator rejudge is rejected instead of overwriting newer data.</p>
 */
@Service
public class JudgeResultService {

    private static final Logger log = LoggerFactory.getLogger(JudgeResultService.class);
    private static final int CASE_MESSAGE_LIMIT = 1024;
    private static final int TEXT_LIMIT = 65_536;

    private final SubmissionMapper submissionMapper;
    private final SubmissionCaseWriter submissionCaseWriter;
    private final ProblemMapper problemMapper;

    public JudgeResultService(SubmissionMapper submissionMapper,
                              SubmissionCaseWriter submissionCaseWriter,
                              ProblemMapper problemMapper) {
        this.submissionMapper = submissionMapper;
        this.submissionCaseWriter = submissionCaseWriter;
        this.problemMapper = problemMapper;
    }

    /**
     * Persists one agent result.
     *
     * <p>A repeated report with the same claim token is idempotent. A report with a different token
     * or a submission that is no longer JUDGING is rejected with a conflict.</p>
     */
    @Transactional
    public void apply(Long submissionId, Long keyId, JudgeResult result) {
        if (result == null || result.claimToken() == null || result.claimToken().isBlank()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "claimToken 不能为空");
        }
        SubmissionStatus status = result.status();
        if (status == null || !status.isFinished()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "判题结果必须是终态");
        }
        validateCases(result.cases());

        Submission submission = submissionMapper.findByIdForUpdate(submissionId);
        if (submission == null) {
            throw BusinessException.of(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        // Duplicate delivery of the same result: the first write already won.
        if (Objects.equals(submission.getClaimToken(), result.claimToken())
                && submission.getJudgedAt() != null) {
            return;
        }
        if (submission.getStatus() != SubmissionStatus.JUDGING
                || !Objects.equals(submission.getClaimToken(), result.claimToken())) {
            throw BusinessException.of(ErrorCode.CONFLICT, "判题任务已被重新领取或已失效");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = submissionMapper.applyResult(submissionId, result.claimToken(), status,
                result.timeUsedMs(), result.memoryUsedKb(),
                truncate(result.compileMessage(), TEXT_LIMIT),
                truncate(result.judgeMessage(), TEXT_LIMIT), now);
        if (updated == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "判题任务已被重新领取或已失效");
        }

        List<SubmissionCase> rows = new ArrayList<>(result.cases().size());
        for (JudgeCaseResult item : result.cases()) {
            SubmissionCase row = new SubmissionCase();
            row.setSubmissionId(submissionId);
            row.setCaseOrdinal(item.ordinal());
            row.setStatus(item.status());
            row.setTimeUsedMs(item.timeUsedMs());
            row.setMemoryUsedKb(item.memoryUsedKb());
            row.setMessage(truncate(item.message(), CASE_MESSAGE_LIMIT));
            rows.add(row);
        }
        submissionCaseWriter.replace(submissionId, rows);
        if (status.isAccepted()) {
            problemMapper.increaseAcceptedCount(submission.getProblemId());
        }
        log.info("远程判题完成 submissionId={} status={} keyId={} nodeId={}",
                submissionId, status, keyId, submission.getJudgeNodeId());
    }

    private void validateCases(List<JudgeCaseResult> cases) {
        Set<Integer> ordinals = new HashSet<>();
        for (JudgeCaseResult item : cases) {
            if (item == null || item.ordinal() <= 0 || item.status() == null || !item.status().isFinished()) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "逐测试点结果不合法");
            }
            if (!ordinals.add(item.ordinal())) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "逐测试点序号不能重复");
            }
            if (item.timeUsedMs() != null && item.timeUsedMs() < 0) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "测试点耗时不能为负数");
            }
            if (item.memoryUsedKb() != null && item.memoryUsedKb() < 0) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "测试点内存不能为负数");
            }
        }
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}
