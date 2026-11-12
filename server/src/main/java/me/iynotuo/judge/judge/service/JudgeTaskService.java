package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.problem.entity.Problem;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import me.iynotuo.judge.problem.mapper.ProblemMapper;
import me.iynotuo.judge.problem.mapper.ProblemTestcaseMapper;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTaskClaimRequest;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.SubmissionStatus;
import me.iynotuo.judge.submission.entity.Submission;
import me.iynotuo.judge.submission.mapper.SubmissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Claim, heartbeat and release operations for remote judge agents.
 */
@Service
public class JudgeTaskService {

    private static final Logger log = LoggerFactory.getLogger(JudgeTaskService.class);

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTestcaseMapper testcaseMapper;
    private final JudgeProperties judgeProperties;
    private final JudgeKeyService judgeKeyService;
    private final JudgeNodeService judgeNodeService;

    public JudgeTaskService(SubmissionMapper submissionMapper,
                            ProblemMapper problemMapper,
                            ProblemTestcaseMapper testcaseMapper,
                            JudgeProperties judgeProperties,
                            JudgeKeyService judgeKeyService,
                            JudgeNodeService judgeNodeService) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.testcaseMapper = testcaseMapper;
        this.judgeProperties = judgeProperties;
        this.judgeKeyService = judgeKeyService;
        this.judgeNodeService = judgeNodeService;
    }

    /**
     * Claims up to {@code maxTasks} pending submissions for one agent.
     *
     * <p>The whole batch runs in one transaction. Each row is locked with {@code FOR UPDATE} before it
     * is marked JUDGING, so two agents cannot claim the same submission.</p>
     */
    @Transactional
    public List<JudgeTask> claim(Long keyId, JudgeTaskClaimRequest request) {
        if (request == null || request.nodeId() == null || request.nodeId().isBlank()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "nodeId 不能为空");
        }
        List<Language> languages = request.languages();
        judgeNodeService.ensureNode(request.nodeId(), keyId, languages);

        int limit = Math.min(Math.max(1, request.maxTasks()),
                Math.max(1, judgeProperties.getDispatch().getMaxClaimBatch()));
        int leaseSeconds = Math.max(30, judgeProperties.getDispatch().getLeaseSeconds());
        List<JudgeTask> tasks = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Long id = submissionMapper.lockNextPendingIdForLanguages(languages);
            if (id == null) {
                break;
            }
            String claimToken = UUID.randomUUID().toString().replace("-", "");
            LocalDateTime now = LocalDateTime.now();
            int claimed = submissionMapper.markClaimed(id, keyId, request.nodeId().trim(),
                    claimToken, now, now.plusSeconds(leaseSeconds));
            if (claimed == 0) {
                continue;
            }
            JudgeTask task = buildTask(id, claimToken, leaseSeconds);
            if (task == null) {
                submissionMapper.failClaim(id, claimToken, "题目或测试数据已不可用，无法判题", LocalDateTime.now());
                continue;
            }
            tasks.add(task);
        }
        if (!tasks.isEmpty()) {
            judgeKeyService.touch(keyId);
            log.info("判题任务领取 keyId={} nodeId={} tasks={}", keyId, request.nodeId(), tasks.size());
        }
        return tasks;
    }

    /**
     * Renews a task lease. Returns {@code cancel=true} when the claim is no longer current.
     */
    @Transactional
    public JudgeHeartbeatResponse heartbeat(Long submissionId, String claimToken) {
        int leaseSeconds = Math.max(30, judgeProperties.getDispatch().getLeaseSeconds());
        if (claimToken == null || claimToken.isBlank()) {
            return new JudgeHeartbeatResponse(0, true);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(leaseSeconds);
        int updated = submissionMapper.extendLease(submissionId, claimToken, now, expiresAt);
        if (updated == 0) {
            return new JudgeHeartbeatResponse(0, true);
        }
        return new JudgeHeartbeatResponse(leaseSeconds, false);
    }

    /**
     * Returns an unstarted task to the queue during a graceful agent shutdown.
     */
    @Transactional
    public void release(Long submissionId, String claimToken) {
        if (claimToken == null || claimToken.isBlank()) {
            return;
        }
        submissionMapper.releaseClaim(submissionId, claimToken);
    }

    private JudgeTask buildTask(Long submissionId, String claimToken, int leaseSeconds) {
        Submission submission = submissionMapper.findById(submissionId);
        if (submission == null) {
            return null;
        }
        Problem problem = problemMapper.findById(submission.getProblemId());
        if (problem == null) {
            return null;
        }
        JudgeProperties.LanguageConfig language = judgeProperties.getLanguages().get(submission.getLanguage());
        if (language == null) {
            return null;
        }
        List<ProblemTestcase> testcases = testcaseMapper.findByProblemId(problem.getId());
        if (testcases.isEmpty()) {
            return null;
        }
        String checkerCode = problemMapper.findCheckerCode(problem.getId());
        long testcaseBytes = testcases.stream()
                .mapToLong(testcase -> utf8Length(testcase.getInputData()) + utf8Length(testcase.getOutputData()))
                .sum();
        boolean bundle = testcaseBytes > Math.max(0, judgeProperties.getDispatch().getInlineMaxBytes());
        List<JudgeTestCase> judgeTestcases = bundle ? List.of() : testcases.stream()
                .map(testcase -> new JudgeTestCase(testcase.getOrdinal(),
                        testcase.getInputData(), testcase.getOutputData()))
                .toList();
        String bundleUrl = bundle ? "/internal/judge/tasks/" + submission.getId() + "/testcases" : null;
        JudgeLanguageConfig languageConfig = new JudgeLanguageConfig(
                language.getSourceFile(),
                language.getCompileCommand(),
                language.getRunCommand(),
                language.getRunWrapper(),
                language.getTimeMultiplier(),
                language.getIsolateProcesses(),
                language.getIsolateMemoryExtraMb(),
                language.isCopySourceToRun(),
                language.getProbeCommands());
        return new JudgeTask(
                submission.getId(),
                submission.getId(),
                claimToken,
                leaseSeconds,
                submission.getLanguage(),
                submission.getCode(),
                problem.getTimeLimitMs() == null ? 1000 : problem.getTimeLimitMs(),
                problem.getMemoryLimitMb() == null ? 256 : problem.getMemoryLimitMb(),
                languageConfig,
                checkerCode,
                judgeTestcases,
                bundleUrl,
                null);
    }

    /**
     * Returns the full test data for a task that was claimed in bundle mode.
     */
    public List<JudgeTestCase> testcases(Long submissionId, String claimToken) {
        if (claimToken == null || claimToken.isBlank()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "claimToken 不能为空");
        }
        Submission submission = submissionMapper.findById(submissionId);
        if (submission == null) {
            throw BusinessException.of(ErrorCode.SUBMISSION_NOT_FOUND);
        }
        if (submission.getStatus() != SubmissionStatus.JUDGING
                || !claimToken.equals(submission.getClaimToken())) {
            throw BusinessException.of(ErrorCode.CONFLICT, "判题任务已被重新领取或已失效");
        }
        return testcaseMapper.findByProblemId(submission.getProblemId()).stream()
                .map(testcase -> new JudgeTestCase(testcase.getOrdinal(),
                        testcase.getInputData(), testcase.getOutputData()))
                .toList();
    }

    private long utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
