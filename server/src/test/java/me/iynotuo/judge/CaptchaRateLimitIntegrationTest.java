package me.iynotuo.judge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The challenge table doubles as the per-IP issuance ledger; this test pins the minute-window limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ioj-captcha-limit;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ioj.captcha.enabled=true",
                "ioj.captcha.issue-per-minute-per-ip=2",
                "ioj.captcha.issue-per-hour-per-ip=100"
        })
@ActiveProfiles("test")
class CaptchaRateLimitIntegrationTest extends ApiTestSupport {

    @Test
    void limitsChallengeIssuancePerIp() throws Exception {
        assertThat(issue("LOGIN").path("code").asInt()).isZero();
        assertThat(issue("LOGIN").path("code").asInt()).isZero();

        HttpResponse<String> limited = exchange(request("/api/v1/captcha")
                .POST(body(Map.of("purpose", "LOGIN"))), null);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(JSON.readTree(limited.body()).path("code").asInt()).isEqualTo(1006);
    }

    private JsonNode issue(String purpose) throws Exception {
        return post("/api/v1/captcha", null, Map.of("purpose", purpose));
    }
}
