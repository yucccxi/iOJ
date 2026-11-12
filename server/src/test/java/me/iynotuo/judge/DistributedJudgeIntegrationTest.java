package me.iynotuo.judge;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.judge.service.JudgeLeaseReaper;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the remote judge protocol: key creation, claim, heartbeat, result,
 * idempotency, stale-result rejection, node registry and lease reaping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ioj-distributed;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ioj.judge.mode=remote",
                "ioj.judge.engine=none",
                "ioj.judge.dispatch.inline-max-bytes=0"
        })
@ActiveProfiles("test")
class DistributedJudgeIntegrationTest extends ApiTestSupport {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JudgeLeaseReaper judgeLeaseReaper;

    @Test
    void remoteAgentClaimsJudgesAndReportsResult() throws Exception {
        String adminToken = createAdmin("distadmin");
        long problemId = createProblemWithOneCase(adminToken);
        long submissionId = submitAcceptedJava(adminToken, problemId);

        JsonNode createdKey = post("/api/v1/admin/judge-keys", adminToken, Map.of("name", "judge-1"));
        assertThat(createdKey.path("code").asInt()).isZero();
        String judgeKey = createdKey.path("data").path("key").asString();
        assertThat(judgeKey).startsWith("jsk_");

        JsonNode bootstrap = get("/internal/judge/config", judgeKey);
        assertThat(bootstrap.path("code").asInt()).isZero();
        assertThat(bootstrap.path("data").path("protocolVersion").asInt()).isPositive();
        assertThat(bootstrap.path("data").path("requiredLanguages").toString()).contains("JAVA");
        assertThat(bootstrap.path("data").path("languages").path("JAVA").path("probeCommands").size())
                .isPositive();
        assertThat(bootstrap.path("data").path("checkerEnabled").asBoolean()).isTrue();
        assertThat(bootstrap.path("data").path("checkerProbeCommands").size()).isPositive();

        // A normal user cannot create agent keys.
        JsonNode registerBob = post("/api/v1/auth/register", null, Map.of(
                "username", "distbob", "email", "distbob@example.com", "password", "password123"));
        String bobToken = post("/api/v1/auth/login", null,
                Map.of("account", "distbob", "password", "password123"))
                .path("data").path("token").asString();
        assertThat(exchange(request("/api/v1/admin/judge-keys")
                .POST(body(Map.of("name", "bob-key"))), bobToken).statusCode()).isEqualTo(403);
        assertThat(exchange(request("/api/v1/admin/judge-keys")
                .POST(body(Map.of("name", "anon-key"))), null).statusCode()).isEqualTo(401);

        // Node heartbeat registers the agent and its language capability.
        JsonNode heartbeat = post("/internal/judge/nodes/heartbeat", judgeKey, Map.of(
                "nodeId", "judge-1",
                "hostname", "judge-test",
                "version", "0.0.1",
                "languages", List.of("JAVA"),
                "maxConcurrency", 1,
                "runningTasks", 0,
                "isolateVersion", "test"));
        assertThat(heartbeat.path("code").asInt()).isZero();

        // Claim one task. The response must include the code, testcases and a fencing token.
        JsonNode claim = post("/internal/judge/tasks/claim", judgeKey, Map.of(
                "nodeId", "judge-1",
                "maxTasks", 1,
                "languages", List.of("JAVA"),
                "protocolVersion", 1));
        assertThat(claim.path("code").asInt()).isZero();
        JsonNode tasks = claim.path("data").path("tasks");
        assertThat(tasks.size()).isEqualTo(1);
        JsonNode task = tasks.get(0);
        String claimToken = task.path("claimToken").asString();
        assertThat(claimToken).isNotBlank();
        assertThat(task.path("submissionId").asLong()).isEqualTo(submissionId);
        assertThat(task.path("testcases").size()).isZero();
        assertThat(task.path("testcaseBundleUrl").asString()).contains("/testcases");
        JsonNode bundle = get("/internal/judge/tasks/" + submissionId
                + "/testcases?claimToken=" + claimToken, judgeKey);
        assertThat(bundle.path("data").size()).isEqualTo(1);
        assertThat(bundle.path("data").get(0).path("input").asString()).isEqualTo("1 2\n");

        // Agents that ask for ZIP receive a compressed bundle with one testcases.json entry.
        HttpResponse<byte[]> zipResponse = client.send(request("/internal/judge/tasks/" + submissionId
                        + "/testcases?claimToken=" + claimToken)
                        .header("Authorization", "Bearer " + judgeKey)
                        .header("Accept", "application/zip")
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(zipResponse.statusCode()).isEqualTo(200);
        assertThat(zipResponse.headers().firstValue("Content-Type").orElse(""))
                .contains("application/zip");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipResponse.body()))) {
            ZipEntry entry = zip.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("testcases.json");
            List<JudgeTestCase> zipped = JSON.readValue(zip,
                    new TypeReference<List<JudgeTestCase>>() {
                    });
            assertThat(zipped).hasSize(1);
            assertThat(zipped.get(0).input()).isEqualTo("1 2\n");
        }
        assertThat(get("/api/v1/submissions/" + submissionId, adminToken)
                .path("data").path("status").asString()).isEqualTo("JUDGING");

        // Heartbeat renews the lease.
        JsonNode taskHeartbeat = post("/internal/judge/tasks/" + submissionId + "/heartbeat", judgeKey, Map.of(
                "nodeId", "judge-1",
                "claimToken", claimToken,
                "runningTasks", 1,
                "protocolVersion", 1));
        assertThat(taskHeartbeat.path("data").path("cancel").asBoolean()).isFalse();
        assertThat(taskHeartbeat.path("data").path("leaseSeconds").asInt()).isPositive();

        // Report the result.
        Map<String, Object> result = Map.of(
                "claimToken", claimToken,
                "status", "ACCEPTED",
                "timeUsedMs", 5,
                "memoryUsedKb", 1024,
                "cases", List.of(Map.of(
                        "ordinal", 1,
                        "status", "ACCEPTED",
                        "timeUsedMs", 5,
                        "memoryUsedKb", 1024)));
        JsonNode reported = post("/internal/judge/tasks/" + submissionId + "/result", judgeKey, result);
        assertThat(reported.path("code").asInt()).isZero();

        JsonNode detail = get("/api/v1/submissions/" + submissionId, adminToken);
        assertThat(detail.path("data").path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(detail.path("data").path("cases").size()).isEqualTo(1);
        assertThat(get("/api/v1/problems/" + problemId, adminToken)
                .path("data").path("acceptedCount").asInt()).isEqualTo(1);

        // The same result can be delivered twice; accepted_count must not double.
        JsonNode duplicate = post("/internal/judge/tasks/" + submissionId + "/result", judgeKey, result);
        assertThat(duplicate.path("code").asInt()).isZero();
        assertThat(get("/api/v1/problems/" + problemId, adminToken)
                .path("data").path("acceptedCount").asInt()).isEqualTo(1);

        // A stale claim token is rejected.
        long secondSubmission = submitAcceptedJava(adminToken, problemId);
        JsonNode secondClaim = post("/internal/judge/tasks/claim", judgeKey, Map.of(
                "nodeId", "judge-1", "maxTasks", 1, "languages", List.of("JAVA"), "protocolVersion", 1));
        String secondToken = secondClaim.path("data").path("tasks").get(0).path("claimToken").asString();
        Map<String, Object> staleResult = Map.of(
                "claimToken", secondToken + "-stale",
                "status", "ACCEPTED",
                "cases", List.of());
        HttpResponse<String> stale = exchange(
                request("/internal/judge/tasks/" + secondSubmission + "/result")
                        .POST(body(staleResult)), judgeKey);
        assertThat(stale.statusCode()).isEqualTo(409);

        assertThat(get("/api/v1/admin/judge-nodes", adminToken)
                .path("data").get(0).path("nodeId").asString()).isEqualTo("judge-1");

        // Expired leases are requeued, not left in JUDGING forever.
        long thirdSubmission = submitAcceptedJava(adminToken, problemId);
        JsonNode thirdClaim = post("/internal/judge/tasks/claim", judgeKey, Map.of(
                "nodeId", "judge-1", "maxTasks", 1, "languages", List.of("JAVA"), "protocolVersion", 1));
        assertThat(thirdClaim.path("data").path("tasks").size()).isEqualTo(1);
        jdbcTemplate.update("UPDATE submissions SET lease_expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusSeconds(5), thirdSubmission);
        judgeLeaseReaper.reap();
        assertThat(get("/api/v1/submissions/" + thirdSubmission, adminToken)
                .path("data").path("status").asString()).isEqualTo("PENDING");
    }

    private String createAdmin(String username) throws Exception {
        post("/api/v1/auth/register", null, Map.of(
                "username", username,
                "email", username + "@example.com",
                "password", "password123"));
        User user = userMapper.findByUsername(username);
        userMapper.updateRole(user.getId(), UserRole.ADMIN, LocalDateTime.now());
        return post("/api/v1/auth/login", null,
                Map.of("account", username, "password", "password123"))
                .path("data").path("token").asString();
    }

    private long createProblemWithOneCase(String adminToken) throws Exception {
        JsonNode created = post("/api/v1/problems", adminToken, Map.of(
                "title", "Distributed A+B",
                "difficulty", "EASY",
                "description", "计算 a+b",
                "timeLimitMs", 2000,
                "memoryLimitMb", 256,
                "samples", List.of(Map.of("input", "1 2\n", "output", "3\n"))));
        long problemId = created.path("data").path("id").asLong();
        JsonNode testcases = put("/api/v1/problems/" + problemId + "/testcases", adminToken,
                List.of(Map.of("input", "1 2\n", "output", "3\n")));
        assertThat(testcases.path("data").size()).isEqualTo(1);
        return problemId;
    }

    private long submitAcceptedJava(String adminToken, long problemId) throws Exception {
        JsonNode submitted = post("/api/v1/submissions", adminToken, Map.of(
                "problemId", problemId,
                "language", "JAVA",
                "code", "public class Main { public static void main(String[] args) { System.out.println(3); } }"));
        assertThat(submitted.path("code").asInt()).isZero();
        return submitted.path("data").path("id").asLong();
    }
}
