package me.iynotuo.judge.agent;

import me.iynotuo.judge.protocol.JudgeApiResponse;
import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatRequest;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeReleaseRequest;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTaskClaimRequest;
import me.iynotuo.judge.protocol.JudgeTaskClaimResponse;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * HTTP implementation of the internal judge protocol.
 */
@Component
public class HttpJudgeAgentClient implements JudgeAgentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpJudgeAgentClient.class);
    private static final int REASON_LIMIT = 200;

    private final AgentProperties properties;
    private final JsonMapper json;
    private final HttpClient httpClient;
    private final String baseUrl;

    public HttpJudgeAgentClient(AgentProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
        this.baseUrl = trimTrailingSlash(properties.getServerUrl());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                .build();
    }

    @Override
    public JudgeBootstrapConfig fetchConfig() {
        return get("/internal/judge/config",
                new TypeReference<JudgeApiResponse<JudgeBootstrapConfig>>() {
                });
    }

    @Override
    public List<JudgeTask> claim(int maxTasks, List<Language> languages) {
        JudgeTaskClaimRequest request = new JudgeTaskClaimRequest(
                properties.getNodeId(), maxTasks, languages, ProtocolVersion.CURRENT);
        JudgeTaskClaimResponse response = post("/internal/judge/tasks/claim", request,
                new TypeReference<JudgeApiResponse<JudgeTaskClaimResponse>>() {
                });
        return response == null ? List.of() : response.tasks();
    }

    @Override
    public List<JudgeTestCase> fetchTestcases(Long taskId, String claimToken) {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new IllegalStateException("ioj.agent.key 未配置，无法访问主服务器");
        }
        String path = "/internal/judge/tasks/" + taskId + "/testcases?claimToken=" + claimToken;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getKey().trim())
                    .header("Accept", "application/zip, application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new AgentClientException("HTTP " + response.statusCode() + ": "
                            + new String(body.readAllBytes(), StandardCharsets.UTF_8));
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (contentType.toLowerCase(Locale.ROOT).contains("zip")) {
                    return readTestcaseZip(body);
                }
                JudgeApiResponse<List<JudgeTestCase>> envelope = json.readValue(body,
                        new TypeReference<JudgeApiResponse<List<JudgeTestCase>>>() {
                        });
                if (envelope.code() != 0) {
                    throw new AgentClientException("业务错误 " + envelope.code() + ": " + envelope.message());
                }
                return envelope.data() == null ? List.of() : envelope.data();
            }
        } catch (AgentClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentClientException("下载测试数据失败: " + ex.getMessage(), ex);
        }
    }

    private List<JudgeTestCase> readTestcaseZip(InputStream body) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(body)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("testcases.json".equals(entry.getName())) {
                    List<JudgeTestCase> testcases = json.readValue(zip,
                            new TypeReference<List<JudgeTestCase>>() {
                            });
                    return testcases == null ? List.of() : testcases;
                }
            }
        }
        throw new AgentClientException("测试数据 bundle 中缺少 testcases.json");
    }

    @Override
    public JudgeNodeHeartbeatResponse heartbeatNode(JudgeNodeInfo info) {
        return post("/internal/judge/nodes/heartbeat", info,
                new TypeReference<JudgeApiResponse<JudgeNodeHeartbeatResponse>>() {
                });
    }

    @Override
    public JudgeHeartbeatResponse heartbeatTask(Long taskId, String claimToken) {
        JudgeHeartbeatRequest request = new JudgeHeartbeatRequest(
                properties.getNodeId(), claimToken, 0, ProtocolVersion.CURRENT);
        JudgeHeartbeatResponse response = post("/internal/judge/tasks/" + taskId + "/heartbeat", request,
                new TypeReference<JudgeApiResponse<JudgeHeartbeatResponse>>() {
                });
        return response == null ? new JudgeHeartbeatResponse(0, true) : response;
    }

    @Override
    public void reportResult(Long taskId, JudgeResult result) {
        post("/internal/judge/tasks/" + taskId + "/result", result,
                new TypeReference<JudgeApiResponse<Void>>() {
                });
    }

    @Override
    public void release(Long taskId, String claimToken, String reason) {
        String safeReason = normalizeReason(reason);
        log.info("释放判题任务 taskId={} reason={}", taskId, safeReason);
        post("/internal/judge/tasks/" + taskId + "/release",
                new JudgeReleaseRequest(claimToken, safeReason),
                new TypeReference<JudgeApiResponse<Void>>() {
                });
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return "";
        }
        String value = reason.replaceAll("\\s+", " ").trim();
        return value.length() <= REASON_LIMIT ? value : value.substring(0, REASON_LIMIT);
    }

    private <T> T post(String path, Object body, TypeReference<JudgeApiResponse<T>> responseType) {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new IllegalStateException("ioj.agent.key 未配置，无法访问主服务器");
        }
        try {
            String payload = json.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            return execute(request, responseType);
        } catch (AgentClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentClientException("调用主服务器失败: " + ex.getMessage(), ex);
        }
    }

    private <T> T get(String path, TypeReference<JudgeApiResponse<T>> responseType) {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new IllegalStateException("ioj.agent.key 未配置，无法访问主服务器");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getKey().trim())
                    .GET()
                    .build();
            return execute(request, responseType);
        } catch (AgentClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentClientException("调用主服务器失败: " + ex.getMessage(), ex);
        }
    }

    private <T> T execute(HttpRequest request, TypeReference<JudgeApiResponse<T>> responseType)
            throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AgentClientException("HTTP " + response.statusCode() + ": " + response.body());
        }
        if (response.body() == null || response.body().isBlank()) {
            return null;
        }
        JudgeApiResponse<T> envelope = json.readValue(response.body(), responseType);
        if (envelope.code() != 0) {
            throw new AgentClientException("业务错误 " + envelope.code() + ": " + envelope.message());
        }
        return envelope.data();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Transport failure; the agent loop decides whether to retry or back off.
     */
    public static class AgentClientException extends RuntimeException {

        public AgentClientException(String message) {
            super(message);
        }

        public AgentClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
