package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.submission.mapper.SubmissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Requeues tasks whose judge agent stopped heartbeating, and fails tasks that exhausted their retry
 * budget. This replaces the old "reset every JUDGING row on startup" behaviour, which would steal
 * tasks from other agents in a distributed deployment.
 */
@Component
@ConditionalOnProperty(prefix = "ioj.judge", name = "mode", havingValue = "remote", matchIfMissing = true)
public class JudgeLeaseReaper {

    private static final Logger log = LoggerFactory.getLogger(JudgeLeaseReaper.class);

    private final SubmissionMapper submissionMapper;
    private final JudgeProperties judgeProperties;

    public JudgeLeaseReaper(SubmissionMapper submissionMapper, JudgeProperties judgeProperties) {
        this.submissionMapper = submissionMapper;
        this.judgeProperties = judgeProperties;
    }

    @Scheduled(fixedDelayString = "${ioj.judge.dispatch.reaper-interval-ms:30000}")
    public void reap() {
        LocalDateTime now = LocalDateTime.now();
        int maxAttempts = Math.max(1, judgeProperties.getDispatch().getMaxAttempts());
        int requeued = submissionMapper.requeueExpiredLeases(now, maxAttempts);
        int failed = submissionMapper.failExpiredLeases(now, maxAttempts,
                "判题机多次领取任务后未回报结果，任务已终止");
        if (requeued > 0 || failed > 0) {
            log.warn("回收过期判题租约 requeued={} failed={}", requeued, failed);
        }
    }
}
