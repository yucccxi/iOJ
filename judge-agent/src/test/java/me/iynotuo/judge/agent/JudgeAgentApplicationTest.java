package me.iynotuo.judge.agent;

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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real agent Spring context with a fake transport, so component scanning and configuration
 * binding are covered without needing a live main server.
 */
@SpringBootTest(classes = JudgeAgentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "ioj.agent.key=test-key",
                "ioj.agent.node-id=test-node",
                "ioj.agent.poll-interval-ms=10",
                "ioj.agent.max-poll-interval-ms=20",
                "ioj.judge.isolate.check-on-startup=false"
        })
@Import(JudgeAgentApplicationTest.FakeTransport.class)
class JudgeAgentApplicationTest {

    @Test
    void contextLoadsWithAgentWiring() {
        assertThat(true).isTrue();
    }

    @TestConfiguration
    static class FakeTransport {

        @Bean
        @Primary
        AgentBootstrap fakeAgentBootstrap() {
            JudgeLanguageConfig language = new JudgeLanguageConfig(
                    "Main.java", "javac Main.java", "java Main", null, 2.0, 64, 256,
                    false, List.of("true"));
            JudgeBootstrapConfig config = new JudgeBootstrapConfig(
                    ProtocolVersion.CURRENT, "test-config", List.of(Language.JAVA),
                    Map.of(Language.JAVA, language), false, List.of());
            return () -> new AgentBootstrap.BootstrapResult(config);
        }

        @Bean
        @Primary
        JudgeAgentClient fakeJudgeAgentClient() {
            return new JudgeAgentClient() {
                @Override
                public JudgeBootstrapConfig fetchConfig() {
                    return null;
                }

                @Override
                public List<JudgeTask> claim(int maxTasks, List<Language> languages) {
                    return List.of();
                }

                @Override
                public List<JudgeTestCase> fetchTestcases(Long taskId, String claimToken) {
                    return List.of();
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
                public void reportResult(Long taskId, JudgeResult result) {
                }

                @Override
                public void release(Long taskId, String claimToken, String reason) {
                }
            };
        }
    }
}
