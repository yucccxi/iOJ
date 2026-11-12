package me.iynotuo.judge.agent;

import jakarta.annotation.PreDestroy;
import me.iynotuo.judge.core.JudgeOutcome;
import me.iynotuo.judge.core.engine.JudgeEngine;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polls the main server, executes claimed tasks in judge-core and reports results back.
 *
 * <p>The loop is bounded by a semaphore, so an agent never holds more tasks than its configured
 * concurrency. Empty polls back off with jitter instead of hammering the server.</p>
 */
@Component
public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private static final int DEFAULT_TASK_LEASE_SECONDS = 300;
    private static final long MIN_HEARTBEAT_INTERVAL_MS = 1_000L;

    private final AgentProperties properties;
    private final JudgeAgentClient client;
    private final AgentTaskMapper taskMapper;
    private final JudgeEngine judgeEngine;
    private final AgentBootstrap bootstrap;
    private final Semaphore slots;
    private final ExecutorService executor;
    private final ConcurrentHashMap<Long, JudgeTask> activeTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> taskHeartbeatDeadlines = new ConcurrentHashMap<>();
    private final Set<Long> cancelledTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger emptyPolls = new AtomicInteger();
    private final String hostname;

    private Thread loopThread;
    private volatile List<Language> languages = List.of();
    private volatile String configVersion = "";
    private volatile long nodeHeartbeatIntervalMs;
    private volatile long nextNodeHeartbeatAtNanos = Long.MAX_VALUE;

    public AgentRunner(AgentProperties properties,
                       JudgeAgentClient client,
                       AgentTaskMapper taskMapper,
                       JudgeEngine judgeEngine,
                       AgentBootstrap bootstrap) {
        this.properties = properties;
        this.client = client;
        this.taskMapper = taskMapper;
        this.judgeEngine = judgeEngine;
        this.bootstrap = bootstrap;
        this.nodeHeartbeatIntervalMs = Math.max(MIN_HEARTBEAT_INTERVAL_MS,
                properties.getNodeHeartbeatIntervalMs());
        this.slots = new Semaphore(Math.max(1, properties.getConcurrency()));
        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getConcurrency()), runnable -> {
            Thread thread = new Thread(runnable, "judge-agent-task-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.hostname = resolveHostname();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            log.error("ioj.agent.key 未配置，请在判题机配置文件中设置主服务器创建的 key");
            System.exit(78);
            return;
        }
        AgentBootstrap.BootstrapResult bootstrapResult;
        try {
            bootstrapResult = bootstrap.bootstrap();
        } catch (AgentStartupException ex) {
            log.error("{}", ex.getMessage());
            System.exit(ex.exitCode());
            return;
        }
        languages = bootstrapResult.config().requiredLanguages();
        configVersion = bootstrapResult.config().configVersion();
        safeNodeHeartbeat();
        loopThread = new Thread(this::runLoop, "judge-agent-loop");
        loopThread.start();
        log.info("判题 Agent 已启动 nodeId={} server={} concurrency={} languages={} configVersion={} "
                        + "nodeHeartbeatIntervalMs={}",
                properties.getNodeId(), properties.getServerUrl(), properties.getConcurrency(),
                languages, configVersion, nodeHeartbeatIntervalMs);
    }

    @Scheduled(fixedDelayString = "${ioj.agent.heartbeat-tick-ms:1000}")
    public void heartbeat() {
        if (!running.get() || languages.isEmpty()) {
            return;
        }
        if (System.nanoTime() >= nextNodeHeartbeatAtNanos) {
            safeNodeHeartbeat();
        }
        long now = System.nanoTime();
        for (JudgeTask task : activeTasks.values()) {
            Long deadline = taskHeartbeatDeadlines.get(task.taskId());
            if (deadline == null) {
                taskHeartbeatDeadlines.put(task.taskId(),
                        now + taskHeartbeatIntervalNanos(task.leaseSeconds()));
                continue;
            }
            if (now < deadline) {
                continue;
            }
            try {
                JudgeHeartbeatResponse response = client.heartbeatTask(task.taskId(), task.claimToken());
                if (response == null || response.cancel()) {
                    cancelledTasks.add(task.taskId());
                    activeTasks.remove(task.taskId());
                    taskHeartbeatDeadlines.remove(task.taskId());
                    log.warn("主服务器要求取消任务 submissionId={}", task.taskId());
                } else {
                    taskHeartbeatDeadlines.put(task.taskId(), System.nanoTime()
                            + taskHeartbeatIntervalNanos(response.leaseSeconds(), task.leaseSeconds()));
                }
            } catch (Exception ex) {
                log.warn("任务心跳失败 submissionId={}: {}", task.taskId(), ex.getMessage());
                long retryNanos = Math.min(taskHeartbeatIntervalNanos(task.leaseSeconds()),
                        TimeUnit.SECONDS.toNanos(5));
                taskHeartbeatDeadlines.put(task.taskId(), System.nanoTime()
                        + Math.max(TimeUnit.SECONDS.toNanos(1), retryNanos));
            }
        }
    }

    private void runLoop() {
        while (running.get()) {
            int free = slots.availablePermits();
            if (free <= 0) {
                pause(200);
                continue;
            }
            try {
                int batch = Math.min(free, Math.max(1, properties.getMaxClaimBatch()));
                List<JudgeTask> tasks = client.claim(batch, languages);
                if (tasks.isEmpty()) {
                    pause(backoff(emptyPolls.incrementAndGet()));
                    continue;
                }
                emptyPolls.set(0);
                for (JudgeTask task : tasks) {
                    if (!slots.tryAcquire()) {
                        safeRelease(task, "no free slot");
                        continue;
                    }
                    activeTasks.put(task.taskId(), task);
                    taskHeartbeatDeadlines.put(task.taskId(),
                            System.nanoTime() + taskHeartbeatIntervalNanos(task.leaseSeconds()));
                    executor.execute(() -> execute(task));
                }
            } catch (Exception ex) {
                log.warn("领取判题任务失败: {}", ex.getMessage());
                pause(backoff(emptyPolls.incrementAndGet()));
            }
        }
    }

    private void execute(JudgeTask task) {
        try {
            if (cancelledTasks.remove(task.taskId())) {
                return;
            }
            List<JudgeTestCase> testcases = task.testcases();
            if (testcases.isEmpty() && task.testcaseBundleUrl() != null) {
                testcases = client.fetchTestcases(task.taskId(), task.claimToken());
            }
            JudgeOutcome outcome = judgeEngine.judge(taskMapper.toContext(task, testcases));
            if (cancelledTasks.remove(task.taskId())) {
                return;
            }
            reportWithRetry(task, taskMapper.toResult(task, outcome));
        } catch (Exception ex) {
            log.error("判题执行失败 submissionId={}", task.taskId(), ex);
            reportWithRetry(task, taskMapper.systemError(task, "判题机执行失败: " + ex.getMessage()));
        } finally {
            activeTasks.remove(task.taskId());
            taskHeartbeatDeadlines.remove(task.taskId());
            slots.release();
        }
    }

    private void reportWithRetry(JudgeTask task, JudgeResult result) {
        long[] delays = {1_000L, 2_000L, 5_000L};
        for (int attempt = 0; attempt < delays.length; attempt++) {
            try {
                client.reportResult(task.taskId(), result);
                return;
            } catch (Exception ex) {
                log.warn("回报判题结果失败 submissionId={} attempt={}: {}",
                        task.taskId(), attempt + 1, ex.getMessage());
                if (attempt < delays.length - 1) {
                    pause(delays[attempt]);
                }
            }
        }
        log.error("回报判题结果最终失败 submissionId={}，等待租约过期后重领", task.taskId());
    }

    private void safeNodeHeartbeat() {
        try {
            JudgeNodeHeartbeatResponse response = client.heartbeatNode(nodeInfo());
            if (response != null && response.offlineSeconds() > 0) {
                long interval = heartbeatIntervalMs(response.offlineSeconds());
                if (interval != nodeHeartbeatIntervalMs) {
                    log.info("节点心跳间隔更新 offlineSeconds={} heartbeatIntervalMs={}",
                            response.offlineSeconds(), interval);
                    nodeHeartbeatIntervalMs = interval;
                }
            }
        } catch (Exception ex) {
            log.warn("节点心跳失败: {}", ex.getMessage());
        } finally {
            nextNodeHeartbeatAtNanos = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(nodeHeartbeatIntervalMs);
        }
    }

    private long heartbeatIntervalMs(int seconds) {
        return Math.max(MIN_HEARTBEAT_INTERVAL_MS, seconds * 1000L / 3);
    }

    private long taskHeartbeatIntervalNanos(int leaseSeconds) {
        return taskHeartbeatIntervalNanos(leaseSeconds, DEFAULT_TASK_LEASE_SECONDS);
    }

    private long taskHeartbeatIntervalNanos(int responseLeaseSeconds, int taskLeaseSeconds) {
        int leaseSeconds = responseLeaseSeconds > 0 ? responseLeaseSeconds : taskLeaseSeconds;
        int effectiveLease = leaseSeconds > 0 ? leaseSeconds : DEFAULT_TASK_LEASE_SECONDS;
        return TimeUnit.MILLISECONDS.toNanos(heartbeatIntervalMs(effectiveLease));
    }

    private JudgeNodeInfo nodeInfo() {
        return new JudgeNodeInfo(
                properties.getNodeId(),
                hostname,
                properties.getVersion(),
                languages,
                Math.max(1, properties.getConcurrency()),
                activeTasks.size(),
                null);
    }

    private void safeRelease(JudgeTask task, String reason) {
        try {
            client.release(task.taskId(), task.claimToken(), reason);
        } catch (Exception ex) {
            log.warn("释放任务失败 submissionId={} reason={}: {}",
                    task.taskId(), reason, ex.getMessage());
        }
    }

    private long backoff(int empty) {
        long base = Math.max(100, properties.getPollIntervalMs());
        long max = Math.max(base, properties.getMaxPollIntervalMs());
        long delay = Math.min(max, base * Math.min(5, Math.max(1, empty)));
        return delay + ThreadLocalRandom.current().nextLong(0, 300);
    }

    private void pause(long millis) {
        try {
            Thread.sleep(Math.max(1, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (loopThread != null) {
            loopThread.interrupt();
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                activeTasks.values().forEach(task -> safeRelease(task, "agent shutdown"));
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("判题 Agent 已停止 nodeId={}", properties.getNodeId());
    }
}
