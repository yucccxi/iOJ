package me.iynotuo.judge.agent;

import me.iynotuo.judge.core.JudgeOutcome;
import me.iynotuo.judge.core.engine.JudgeEngine;
import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.ProtocolVersion;
import me.iynotuo.judge.protocol.SubmissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the agent loop with a fake transport and a fake engine.
 */
class AgentRunnerTest {

    @Test
    void claimsExecutesAndReportsOneTask() throws Exception {
        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<JudgeResult> result = new AtomicReference<>();
        JudgeTask task = task();
        FakeClient client = new FakeClient(task, reported, result);
        AgentProperties properties = new AgentProperties();
        properties.setKey("jsk_test");
        properties.setNodeId("judge-1");
        properties.setConcurrency(1);
        properties.setMaxClaimBatch(1);
        properties.setPollIntervalMs(10);
        properties.setMaxPollIntervalMs(20);

        JudgeBootstrapConfig config = new JudgeBootstrapConfig(
                ProtocolVersion.CURRENT, "test-config", List.of(Language.JAVA),
                Map.of(Language.JAVA, task.languageConfig()), false, List.of());
        AgentBootstrap bootstrap = () -> new AgentBootstrap.BootstrapResult(config);

        JudgeEngine engine = context -> new JudgeOutcome(SubmissionStatus.ACCEPTED, null, null,
                List.of(new JudgeOutcome.CaseResult(1, SubmissionStatus.ACCEPTED, 1, 1024, null)),
                1, 1024);
        AgentRunner runner = new AgentRunner(properties, client, new AgentTaskMapper(), engine, bootstrap);
        try {
            runner.start();
            assertThat(reported.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get().status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(result.get().claimToken()).isEqualTo("claim-token");
        } finally {
            runner.shutdown();
        }
    }

    @Test
    void sendsTaskHeartbeatAtOneThirdOfLease() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reported = new CountDownLatch(1);
        AtomicInteger taskHeartbeats = new AtomicInteger();
        AtomicReference<JudgeResult> result = new AtomicReference<>();
        JudgeTask task = task(3);
        HeartbeatClient client = new HeartbeatClient(task, reported, taskHeartbeats, result);
        AgentProperties properties = new AgentProperties();
        properties.setKey("jsk_test");
        properties.setNodeId("judge-1");
        properties.setConcurrency(1);
        properties.setMaxClaimBatch(1);
        properties.setPollIntervalMs(10);
        properties.setMaxPollIntervalMs(20);

        JudgeBootstrapConfig config = new JudgeBootstrapConfig(
                ProtocolVersion.CURRENT, "test-config", List.of(Language.JAVA),
                Map.of(Language.JAVA, task.languageConfig()), false, List.of());
        AgentBootstrap bootstrap = () -> new AgentBootstrap.BootstrapResult(config);
        JudgeEngine engine = context -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new JudgeOutcome(SubmissionStatus.ACCEPTED, null, null,
                    List.of(new JudgeOutcome.CaseResult(1, SubmissionStatus.ACCEPTED, 1, 1024, null)),
                    1, 1024);
        };

        AgentRunner runner = new AgentRunner(properties, client, new AgentTaskMapper(), engine, bootstrap);
        try {
            runner.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            // The 3-second lease schedules its first task heartbeat after 1 second.
            Thread.sleep(1_100);
            runner.heartbeat();
            assertThat(taskHeartbeats.get()).isGreaterThanOrEqualTo(1);
        } finally {
            release.countDown();
            runner.shutdown();
        }
        assertThat(reported.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private JudgeTask task() {
        return task(300);
    }

    private JudgeTask task(int leaseSeconds) {
        return new JudgeTask(
                1L,
                1L,
                "claim-token",
                leaseSeconds,
                Language.JAVA,
                "public class Main {}",
                1000,
                256,
                new JudgeLanguageConfig("Main.java", "javac Main.java", "java Main", null, 2.0, 64, 256,
                        false, List.of("javac -version")),
                null,
                List.of(new JudgeTestCase(1, "1 2\n", "3\n")),
                null,
                null);
    }

    private static final class HeartbeatClient implements JudgeAgentClient {

        private final JudgeTask task;
        private final CountDownLatch reported;
        private final AtomicInteger taskHeartbeats;
        private final AtomicReference<JudgeResult> result;
        private int claims;

        private HeartbeatClient(JudgeTask task,
                                CountDownLatch reported,
                                AtomicInteger taskHeartbeats,
                                AtomicReference<JudgeResult> result) {
            this.task = task;
            this.reported = reported;
            this.taskHeartbeats = taskHeartbeats;
            this.result = result;
        }

        @Override
        public JudgeBootstrapConfig fetchConfig() {
            return null;
        }

        @Override
        public List<JudgeTask> claim(int maxTasks, List<Language> languages) {
            claims++;
            return claims == 1 ? List.of(task) : List.of();
        }

        @Override
        public List<JudgeTestCase> fetchTestcases(Long taskId, String claimToken) {
            return task.testcases();
        }

        @Override
        public JudgeNodeHeartbeatResponse heartbeatNode(JudgeNodeInfo info) {
            return new JudgeNodeHeartbeatResponse(90);
        }

        @Override
        public JudgeHeartbeatResponse heartbeatTask(Long taskId, String claimToken) {
            taskHeartbeats.incrementAndGet();
            return new JudgeHeartbeatResponse(3, false);
        }

        @Override
        public void reportResult(Long taskId, JudgeResult value) {
            result.set(value);
            reported.countDown();
        }

        @Override
        public void release(Long taskId, String claimToken, String reason) {
        }
    }

    private static final class FakeClient implements JudgeAgentClient {

        private final JudgeTask task;
        private final CountDownLatch reported;
        private final AtomicReference<JudgeResult> result;
        private int claims;

        private FakeClient(JudgeTask task, CountDownLatch reported, AtomicReference<JudgeResult> result) {
            this.task = task;
            this.reported = reported;
            this.result = result;
        }

        @Override
        public JudgeBootstrapConfig fetchConfig() {
            return null;
        }

        @Override
        public List<JudgeTask> claim(int maxTasks, List<Language> languages) {
            claims++;
            return claims == 1 ? List.of(task) : List.of();
        }

        @Override
        public List<JudgeTestCase> fetchTestcases(Long taskId, String claimToken) {
            return task.testcases();
        }

        @Override
        public JudgeNodeHeartbeatResponse heartbeatNode(JudgeNodeInfo info) {
            return new JudgeNodeHeartbeatResponse(90);
        }

        @Override
        public JudgeHeartbeatResponse heartbeatTask(Long taskId, String claimToken) {
            return new JudgeHeartbeatResponse(300, false);
        }

        @Override
        public void reportResult(Long taskId, JudgeResult value) {
            result.set(value);
            reported.countDown();
        }

        @Override
        public void release(Long taskId, String claimToken, String reason) {
        }
    }
}
