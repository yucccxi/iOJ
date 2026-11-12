package me.iynotuo.judge.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.iynotuo.judge.protocol.JudgeApiResponse;
import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTaskClaimResponse;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.ProtocolVersion;
import me.iynotuo.judge.protocol.SubmissionStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the HTTP wire format and bearer authentication of the agent client without a real server.
 */
class HttpJudgeAgentClientTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void claimsAndReportsThroughInternalApi() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> resultBody = new AtomicReference<>();
        AtomicReference<String> releaseBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/internal/judge/nodes/heartbeat", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, JudgeApiResponse.class,
                    new JudgeApiResponse<>(0, "ok", new JudgeNodeHeartbeatResponse(90)));
        });
        server.createContext("/internal/judge/tasks/claim", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            JudgeTask task = task();
            respond(exchange, JudgeApiResponse.class,
                    new JudgeApiResponse<>(0, "ok", new JudgeTaskClaimResponse(List.of(task))));
        });
        server.createContext("/internal/judge/tasks/1/result", exchange -> {
            resultBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, JudgeApiResponse.class, new JudgeApiResponse<>(0, "ok", null));
        });
        server.createContext("/internal/judge/tasks/1/release", exchange -> {
            releaseBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, JudgeApiResponse.class, new JudgeApiResponse<>(0, "ok", null));
        });
        server.createContext("/internal/judge/tasks/1/heartbeat", exchange ->
                respond(exchange, JudgeApiResponse.class,
                        new JudgeApiResponse<>(0, "ok", new JudgeHeartbeatResponse(300, false))));
        server.createContext("/internal/judge/config", exchange -> {
            JudgeLanguageConfig language = new JudgeLanguageConfig(
                    "Main.java", "javac Main.java", "java Main", null, 2.0, 64, 256,
                    false, List.of("javac -version"));
            JudgeBootstrapConfig config = new JudgeBootstrapConfig(
                    ProtocolVersion.CURRENT, "test-config", List.of(Language.JAVA),
                    Map.of(Language.JAVA, language), false, List.of());
            respond(exchange, JudgeApiResponse.class, new JudgeApiResponse<>(0, "ok", config));
        });
        server.start();

        try {
            AgentProperties properties = new AgentProperties();
            properties.setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setKey("jsk_test");
            properties.setNodeId("judge-1");
            HttpJudgeAgentClient client = new HttpJudgeAgentClient(properties, json);

            assertThat(client.fetchConfig().requiredLanguages()).containsExactly(Language.JAVA);

            JudgeNodeHeartbeatResponse nodeHeartbeat = client.heartbeatNode(
                    new JudgeNodeInfo("judge-1", "host", "0.0.1",
                            List.of(Language.JAVA), 1, 0, "2.7"));
            assertThat(nodeHeartbeat.offlineSeconds()).isEqualTo(90);
            assertThat(authorization.get()).isEqualTo("Bearer jsk_test");

            List<JudgeTask> tasks = client.claim(1, List.of(Language.JAVA));
            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).claimToken()).isEqualTo("claim-token");

            JudgeResult result = new JudgeResult("claim-token", SubmissionStatus.ACCEPTED,
                    5, 1024, null, null,
                    List.of(new me.iynotuo.judge.protocol.JudgeCaseResult(1,
                            SubmissionStatus.ACCEPTED, 5, 1024, null)));
            client.reportResult(1L, result);
            assertThat(resultBody.get()).contains("\"claimToken\":\"claim-token\"");
            assertThat(resultBody.get()).contains("\"status\":\"ACCEPTED\"");

            assertThat(client.heartbeatTask(1L, "claim-token").cancel()).isFalse();

            client.release(1L, "claim-token", "no free slot");
            assertThat(releaseBody.get()).contains("\"claimToken\":\"claim-token\"");
            assertThat(releaseBody.get()).contains("\"reason\":\"no free slot\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchesZipTestcaseBundle() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/judge/tasks/1/testcases", exchange -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry("testcases.json"));
                zip.write(json.writeValueAsBytes(
                        List.of(new JudgeTestCase(1, "1 2\n", "3\n"))));
                zip.closeEntry();
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, bytes.size());
            exchange.getResponseBody().write(bytes.toByteArray());
            exchange.close();
        });
        server.start();

        try {
            AgentProperties properties = new AgentProperties();
            properties.setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setKey("jsk_test");
            properties.setNodeId("judge-1");
            HttpJudgeAgentClient client = new HttpJudgeAgentClient(properties, json);

            List<JudgeTestCase> testcases = client.fetchTestcases(1L, "claim-token");
            assertThat(testcases).hasSize(1);
            assertThat(testcases.get(0).input()).isEqualTo("1 2\n");
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, Class<?> ignored, Object body) throws java.io.IOException {
        byte[] bytes = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private JudgeTask task() {
        return new JudgeTask(
                1L,
                1L,
                "claim-token",
                300,
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
}
