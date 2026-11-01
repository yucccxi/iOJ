package me.iynotuo.judge.judge;

import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.judge.engine.JudgeEngine;
import me.iynotuo.judge.problem.entity.Problem;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import me.iynotuo.judge.problem.mapper.ProblemMapper;
import me.iynotuo.judge.problem.mapper.ProblemTestcaseMapper;
import me.iynotuo.judge.submission.entity.Submission;
import me.iynotuo.judge.submission.entity.SubmissionCase;
import me.iynotuo.judge.submission.mapper.SubmissionCaseMapper;
import me.iynotuo.judge.submission.mapper.SubmissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Schedules judging and persists the results.
 *
 * <p>Flow: the worker claims one PENDING submission in a short transaction and marks it JUDGING, then
 * compiles and runs it outside any transaction (long work never holds a database connection), and finally writes the per-case results and the final status in a new transaction.</p>
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final SubmissionMapper submissionMapper;
    private final SubmissionCaseMapper submissionCaseMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTestcaseMapper testcaseMapper;
    private final JudgeEngine judgeEngine;
    private final JudgeProperties judgeProperties;

    /**
     * The judge thread manages its own transaction boundary: judge() calls persist() internally, so a @Transactional annotation there would not take effect.
     */
    private final TransactionTemplate transactionTemplate;

    public JudgeService(SubmissionMapper submissionMapper,
                        SubmissionCaseMapper submissionCaseMapper,
                        ProblemMapper problemMapper,
                        ProblemTestcaseMapper testcaseMapper,
                        JudgeEngine judgeEngine,
                        JudgeProperties judgeProperties,
                        PlatformTransactionManager transactionManager) {
        this.submissionMapper = submissionMapper;
        this.submissionCaseMapper = submissionCaseMapper;
        this.problemMapper = problemMapper;
        this.testcaseMapper = testcaseMapper;
        this.judgeEngine = judgeEngine;
        this.judgeProperties = judgeProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Claims the next pending submission. A row lock guarantees each submission is processed once, even with multiple instances or threads.
     *
     * @return the submission id, or null when the queue is empty
     */
    @Transactional
    public Long claimNext() {
        Long id = submissionMapper.lockNextPendingId();
        if (id == null) {
            return null;
        }
        submissionMapper.markJudging(id);
        return id;
    }

    /**
     * Judges a submission and persists the result. Any exception becomes SYSTEM_ERROR so a submission never stays in JUDGING forever.
     */
    public void judge(Long submissionId) {
        Submission submission = submissionMapper.findById(submissionId);
        if (submission == null) {
            log.warn("待评测提交不存在: {}", submissionId);
            return;
        }
        if (submission.getStatus().isFinished()) {
            return;
        }
        Problem problem = problemMapper.findById(submission.getProblemId());
        List<ProblemTestcase> testcases = problem == null
                ? List.of() : testcaseMapper.findByProblemId(submission.getProblemId());
        // The checker source is read through its own query so it never travels with the problem entity,
        // which the public problem detail endpoint also uses.
        String checkerCode = problem == null ? null : problemMapper.findCheckerCode(submission.getProblemId());
        JudgeProperties.LanguageConfig language = judgeProperties.getLanguages().get(submission.getLanguage());

        JudgeOutcome outcome;
        if (problem == null) {
            outcome = JudgeOutcome.systemError("题目已被删除");
        } else if (language == null) {
            outcome = JudgeOutcome.systemError("语言未启用: " + submission.getLanguage());
        } else if (testcases.isEmpty()) {
            outcome = JudgeOutcome.systemError("题目没有测试点");
        } else {
            try {
                outcome = judgeEngine.judge(new JudgeContext(submission, problem, testcases, language, checkerCode));
            } catch (Exception ex) {
                log.error("判题失败 submissionId={}", submissionId, ex);
                outcome = JudgeOutcome.systemError("判题异常: " + ex.getClass().getSimpleName());
            }
        }
        persist(submission, outcome);
    }

    public void persist(Submission submission, JudgeOutcome outcome) {
        transactionTemplate.executeWithoutResult(status -> doPersist(submission, outcome));
    }

    private void doPersist(Submission submission, JudgeOutcome outcome) {
        submissionCaseMapper.deleteBySubmissionId(submission.getId());
        if (!outcome.cases().isEmpty()) {
            List<SubmissionCase> rows = new ArrayList<>(outcome.cases().size());
            for (JudgeOutcome.CaseResult result : outcome.cases()) {
                SubmissionCase row = new SubmissionCase();
                row.setSubmissionId(submission.getId());
                row.setCaseOrdinal(result.ordinal());
                row.setStatus(result.status());
                row.setTimeUsedMs(result.timeUsedMs());
                row.setMemoryUsedKb(result.memoryUsedKb());
                row.setMessage(result.message());
                rows.add(row);
            }
            submissionCaseMapper.insertBatch(rows);
        }

        Submission update = new Submission();
        update.setId(submission.getId());
        update.setStatus(outcome.status());
        update.setTimeUsedMs(outcome.timeUsedMs());
        update.setMemoryUsedKb(outcome.memoryUsedKb());
        update.setCompileMessage(outcome.compileMessage());
        update.setJudgeMessage(outcome.judgeMessage());
        update.setJudgedAt(LocalDateTime.now());
        submissionMapper.updateResult(update);

        if (outcome.status().isAccepted()) {
            problemMapper.increaseAcceptedCount(submission.getProblemId());
        }
        log.info("判题完成 submissionId={} status={}", submission.getId(), outcome.status());
    }

    /**
     * Recovery after restart: a killed process may leave submissions in JUDGING.
     */
    @Transactional
    public void recoverStuckSubmissions() {
        int count = submissionMapper.resetJudgingToPending();
        if (count > 0) {
            log.warn("已将 {} 条残留的 JUDGING 提交重置为 PENDING", count);
        }
    }
}
