package me.iynotuo.judge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the test-case and code size limits are read from configuration instead of being
 * hard-coded in the request DTOs: with a tiny configured limit, one byte over it must be rejected
 * and the error message must quote the configured value.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ioj-limits;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ioj.problem.max-testcase-bytes=1024",
                "ioj.submission.max-code-length=64"
        })
@ActiveProfiles("test")
class LimitConfigIntegrationTest extends ApiTestSupport {

    /**
     * Covers the per-field byte limit of a test case (single add and batch replace) and the
     * character limit of the submitted source code.
     */
    @Test
    void limitsFollowConfiguration() throws Exception {
        post("/api/v1/auth/register", null, Map.of(
                "username", "limits",
                "email", "limits@example.com",
                "password", "password123"));
        String token = post("/api/v1/auth/login", null,
                Map.of("account", "limits", "password", "password123"))
                .path("data").path("token").asString();
        assertThat(token).isNotBlank();

        long problemId = post("/api/v1/problems", token, Map.of(
                "title", "Limit Check",
                "difficulty", "EASY",
                "description", "验证上限来自配置"))
                .path("data").path("id").asLong();
        assertThat(problemId).isPositive();

        // Exactly at the configured limit (1024 bytes) is accepted
        JsonNode accepted = post("/api/v1/problems/" + problemId + "/testcases", token,
                Map.of("input", "a".repeat(1024), "output", "ok"));
        assertThat(accepted.path("code").asInt()).isZero();

        // One byte over the limit is rejected, and the message quotes the configured value
        HttpResponse<String> tooBig = exchange(request("/api/v1/problems/" + problemId + "/testcases")
                .POST(body(Map.of("input", "a".repeat(1025), "output", "ok"))), token);
        assertThat(tooBig.statusCode()).isEqualTo(400);
        JsonNode rejection = JSON.readTree(tooBig.body());
        assertThat(rejection.path("code").asInt()).isEqualTo(1000);
        assertThat(rejection.path("message").asString()).contains("1024");

        // The batch replace endpoint validates every element with the same limit
        HttpResponse<String> bigBatch = exchange(request("/api/v1/problems/" + problemId + "/testcases")
                .PUT(body(List.of(
                        Map.of("input", "a".repeat(1024), "output", "ok"),
                        Map.of("input", "ok", "output", "b".repeat(1025))))), token);
        assertThat(bigBatch.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(bigBatch.body()).path("code").asInt()).isEqualTo(1000);

        // 65 characters exceed the configured code limit of 64
        HttpResponse<String> longCode = exchange(request("/api/v1/submissions")
                .POST(body(Map.of("problemId", problemId, "language", "JAVA",
                        "code", "x".repeat(65)))), token);
        assertThat(longCode.statusCode()).isEqualTo(400);
        JsonNode codeRejection = JSON.readTree(longCode.body());
        assertThat(codeRejection.path("code").asInt()).isEqualTo(4003);
        assertThat(codeRejection.path("message").asString()).contains("64");
    }
}
