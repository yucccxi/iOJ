package me.iynotuo.judge.judge;

import me.iynotuo.judge.config.JudgeProperties;
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
 * 判题调度与结果落库。
 *
 * <p>流程：worker 在短事务中抢占一条 PENDING 提交并标记为 JUDGING →
 * 事务外执行编译/运行（耗时操作不占用数据库连接）→ 新事务写入逐点结果与最终状态。</p>
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
     * 判题线程自行控制事务边界（judge() 内部调用 persist() 属于自调用，注解事务不会生效）。
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
     * 抢占一条待评测提交。行锁保证多实例/多线程下同一条提交只被处理一次。
     *
     * @return 提交 id；队列为空时返回 null
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
     * 执行判题并落库。异常会转换成 SYSTEM_ERROR，保证提交不会永远停在 JUDGING。
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
                outcome = judgeEngine.judge(new JudgeContext(submission, problem, testcases, language));
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
     * 重启后恢复：上一次进程被杀时可能留下 JUDGING 状态的提交。
     */
    @Transactional
    public void recoverStuckSubmissions() {
        int count = submissionMapper.resetJudgingToPending();
        if (count > 0) {
            log.warn("已将 {} 条残留的 JUDGING 提交重置为 PENDING", count);
        }
    }
}
