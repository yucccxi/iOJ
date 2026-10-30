package me.iynotuo.judge.judge;

import me.iynotuo.judge.config.JudgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Judge worker: periodically fetches pending submissions from the database and hands them to the judge thread pool.
 *
 * <p>No message queue is used. For a club-scale workload (a few hundred submissions per day) a database queue with row locks is the simplest option
 * and is easy to inspect. If submission volume grows, claimNext() can be replaced by a message queue consumer.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ioj.judge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JudgeWorker {

    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);

    private final JudgeService judgeService;
    private final ExecutorService executor;
    private final Semaphore slots;

    public JudgeWorker(JudgeService judgeService,
                       @Qualifier("judgeExecutor") ExecutorService executor,
                       JudgeProperties properties) {
        this.judgeService = judgeService;
        this.executor = executor;
        this.slots = new Semaphore(Math.max(1, properties.getConcurrency()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        judgeService.recoverStuckSubmissions();
    }

    @Scheduled(fixedDelayString = "${ioj.judge.poll-interval-ms:1000}")
    public void poll() {
        while (slots.tryAcquire()) {
            Long submissionId;
            try {
                submissionId = judgeService.claimNext();
            } catch (Exception ex) {
                slots.release();
                log.error("抢占待评测提交失败", ex);
                return;
            }
            if (submissionId == null) {
                slots.release();
                return;
            }
            try {
                executor.execute(() -> {
                    try {
                        judgeService.judge(submissionId);
                    } catch (Exception ex) {
                        log.error("判题任务异常 submissionId={}", submissionId, ex);
                    } finally {
                        slots.release();
                    }
                });
            } catch (Exception ex) {
                slots.release();
                log.error("提交判题任务失败 submissionId={}", submissionId, ex);
                return;
            }
        }
    }
}
