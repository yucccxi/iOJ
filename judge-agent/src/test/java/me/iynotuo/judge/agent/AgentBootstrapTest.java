package me.iynotuo.judge.agent;

import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.ProtocolVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies strict startup validation: every server language must be supported locally.
 */
class AgentBootstrapTest {

    private final AgentProperties agentProperties = new AgentProperties();

    @Test
    void acceptsWhenAllProbesSucceed() {
        DefaultAgentBootstrap bootstrap = bootstrap(config(ProtocolVersion.CURRENT, List.of("true")));
        AgentBootstrap.BootstrapResult result = bootstrap.bootstrap();
        assertThat(result.config().requiredLanguages()).containsExactly(Language.JAVA);
    }

    @Test
    void rejectsMissingToolchain() {
        DefaultAgentBootstrap bootstrap = bootstrap(config(ProtocolVersion.CURRENT, List.of("false")));
        assertThatThrownBy(bootstrap::bootstrap)
                .isInstanceOf(AgentStartupException.class)
                .hasMessageContaining("JAVA")
                .extracting(ex -> ((AgentStartupException) ex).exitCode())
                .isEqualTo(78);
    }

    @Test
    void rejectsProtocolMismatch() {
        DefaultAgentBootstrap bootstrap = bootstrap(config(ProtocolVersion.CURRENT + 1, List.of("true")));
        assertThatThrownBy(bootstrap::bootstrap)
                .isInstanceOf(AgentStartupException.class)
                .hasMessageContaining("协议版本");
    }

    @Test
    void rejectsInvalidKey() {
        DefaultAgentBootstrap bootstrap = new DefaultAgentBootstrap(
                new FakeClient() {
                    @Override
                    public JudgeBootstrapConfig fetchConfig() {
                        throw new HttpJudgeAgentClient.AgentClientException("HTTP 401: unauthorized");
                    }
                },
                new ToolchainProbe(agentProperties), agentProperties, judgeProperties());
        assertThatThrownBy(bootstrap::bootstrap)
                .isInstanceOf(AgentStartupException.class)
                .hasMessageContaining("IOJ_JUDGE_KEY");
    }

    @Test
    void retriesTransientFailureAndExitsTempfail() {
        agentProperties.setConfigFetchRetries(0);
        DefaultAgentBootstrap bootstrap = new DefaultAgentBootstrap(
                new FakeClient() {
                    @Override
                    public JudgeBootstrapConfig fetchConfig() {
                        throw new HttpJudgeAgentClient.AgentClientException("HTTP 500: unavailable");
                    }
                },
                new ToolchainProbe(agentProperties), agentProperties, judgeProperties());
        assertThatThrownBy(bootstrap::bootstrap)
                .isInstanceOf(AgentStartupException.class)
                .extracting(ex -> ((AgentStartupException) ex).exitCode())
                .isEqualTo(75);
    }

    private DefaultAgentBootstrap bootstrap(JudgeBootstrapConfig config) {
        return new DefaultAgentBootstrap(new FakeClient(config),
                new ToolchainProbe(agentProperties), agentProperties, judgeProperties());
    }

    private JudgeProperties judgeProperties() {
        JudgeProperties properties = new JudgeProperties();
        properties.getIsolate().setBinary("/usr/bin/true");
        return properties;
    }

    private JudgeBootstrapConfig config(int protocol, List<String> probes) {
        JudgeLanguageConfig language = new JudgeLanguageConfig(
                "Main.java", "javac Main.java", "java Main", null, 2.0, 64, 256, false, probes);
        return new JudgeBootstrapConfig(protocol, "test-config", List.of(Language.JAVA),
                Map.of(Language.JAVA, language), false, List.of());
    }

    private static class FakeClient implements JudgeAgentClient {

        private final JudgeBootstrapConfig config;

        private FakeClient() {
            this(null);
        }

        private FakeClient(JudgeBootstrapConfig config) {
            this.config = config;
        }

        @Override
        public JudgeBootstrapConfig fetchConfig() {
            return config;
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
        public void heartbeatNode(JudgeNodeInfo info) {
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
    }
}
