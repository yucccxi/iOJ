package me.iynotuo.judge;

import me.iynotuo.judge.captcha.CaptchaCodeGenerator;
import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.user.mail.EmailCodeSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the CAPTCHA contract end to end: image issuance, login gating, purpose binding, one-time
 * use, and the shared {@code EMAIL_SENDING} challenge used by both verification-code mail endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ioj-captcha;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ioj.captcha.enabled=true",
                "ioj.email-code.enabled=true"
        })
@ActiveProfiles("test")
class CaptchaIntegrationTest extends ApiTestSupport {

    private static final String FIXED_CODE = "ABCDE";
    private static final String EMAIL = "captcha-user@example.com";
    private static final String USERNAME = "captchauser";
    private static final String PASSWORD = "password123";

    @Autowired
    private CapturingEmailCodeSender mail;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @TestConfiguration
    static class CaptchaTestConfiguration {

        @Bean
        @Primary
        CaptchaCodeGenerator fixedCaptchaCodeGenerator() {
            return new CaptchaCodeGenerator() {
                @Override
                public String generate(int length) {
                    return FIXED_CODE;
                }
            };
        }

        @Bean
        @Primary
        CapturingEmailCodeSender capturingEmailCodeSender() {
            return new CapturingEmailCodeSender();
        }
    }

    static class CapturingEmailCodeSender implements EmailCodeSender {

        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void send(String email, String code, int ttlMinutes, EmailCodePurpose purpose) {
            codes.put(purpose + ":" + email, code);
        }

        String codeFor(EmailCodePurpose purpose, String email) {
            return codes.get(purpose + ":" + email);
        }
    }

    @Test
    void captchaGatesLoginAndBothMailEndpoints() throws Exception {
        JsonNode registrationCaptcha = issueCaptcha("EMAIL_SENDING");
        String registrationCaptchaId = registrationCaptcha.path("data").path("captchaId").asString();
        assertThat(registrationCaptcha.path("data").path("captchaId").asString()).hasSize(32);
        assertThat(registrationCaptcha.path("data").path("image").asString()).startsWith("data:image/png;base64,");
        assertThat(registrationCaptcha.path("data").path("expiresInSeconds").asInt()).isEqualTo(120);

        // EMAIL_SENDING is accepted by the registration-code endpoint.
        assertThat(post("/api/v1/auth/email-codes", null, Map.of(
                "email", EMAIL,
                "captchaId", registrationCaptchaId,
                "captchaCode", FIXED_CODE)).path("code").asInt()).isZero();
        String registrationCode = mail.codeFor(EmailCodePurpose.REGISTER, EMAIL);
        assertThat(registrationCode).matches("\\d{6}");

        // Register does not require a second CAPTCHA; the email code is the proof.
        assertThat(post("/api/v1/auth/register", null, Map.of(
                "username", USERNAME,
                "email", EMAIL,
                "password", PASSWORD,
                "emailCode", registrationCode)).path("code").asInt()).isZero();

        // Missing CAPTCHA is rejected before credentials are checked.
        HttpResponse<String> missing = exchange(request("/api/v1/auth/login").POST(body(Map.of(
                "account", USERNAME, "password", PASSWORD))), null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(missing.body()).path("code").asInt()).isEqualTo(2010);

        // A challenge issued for another purpose cannot be used on login.
        JsonNode emailCaptcha = issueCaptcha("EMAIL_SENDING");
        HttpResponse<String> wrongPurpose = exchange(request("/api/v1/auth/login").POST(body(Map.of(
                "account", USERNAME,
                "password", PASSWORD,
                "captchaId", emailCaptcha.path("data").path("captchaId").asString(),
                "captchaCode", FIXED_CODE))), null);
        assertThat(wrongPurpose.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(wrongPurpose.body()).path("code").asInt()).isEqualTo(2010);

        JsonNode loginCaptcha = issueCaptcha("LOGIN");
        String loginCaptchaId = loginCaptcha.path("data").path("captchaId").asString();

        // A wrong answer records an attempt but does not consume the challenge before the limit.
        HttpResponse<String> wrongCode = exchange(request("/api/v1/auth/login").POST(body(Map.of(
                "account", USERNAME,
                "password", PASSWORD,
                "captchaId", loginCaptchaId,
                "captchaCode", "ZZZZZ"))), null);
        assertThat(wrongCode.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(wrongCode.body()).path("code").asInt()).isEqualTo(2010);

        assertThat(post("/api/v1/auth/login", null, Map.of(
                "account", USERNAME,
                "password", PASSWORD,
                "captchaId", loginCaptchaId,
                "captchaCode", FIXED_CODE)).path("data").path("token").asString()).isNotBlank();

        // A challenge is single-use.
        HttpResponse<String> reused = exchange(request("/api/v1/auth/login").POST(body(Map.of(
                "account", USERNAME,
                "password", PASSWORD,
                "captchaId", loginCaptchaId,
                "captchaCode", FIXED_CODE))), null);
        assertThat(reused.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(reused.body()).path("code").asInt()).isEqualTo(2010);

        // The other mail endpoint shares the same EMAIL_SENDING purpose. Use a separately inserted
        // account so the shared per-email send cooldown does not interfere with the registration code.
        String resetEmail = "captcha-reset@example.com";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        INSERT INTO users (username, email, password_hash, nickname, role, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?)
                        """,
                "captchareset", resetEmail, passwordEncoder.encode(PASSWORD), "Captcha Reset",
                Timestamp.valueOf(now), Timestamp.valueOf(now));
        JsonNode resetCaptcha = issueCaptcha("EMAIL_SENDING");
        assertThat(post("/api/v1/auth/password-reset-codes", null, Map.of(
                "email", resetEmail,
                "captchaId", resetCaptcha.path("data").path("captchaId").asString(),
                "captchaCode", FIXED_CODE)).path("code").asInt()).isZero();
        assertThat(mail.codeFor(EmailCodePurpose.RESET_PASSWORD, resetEmail)).matches("\\d{6}");

        // The registration challenge was already consumed and cannot be replayed on reset.
        HttpResponse<String> replayed = exchange(request("/api/v1/auth/password-reset-codes").POST(body(Map.of(
                "email", resetEmail,
                "captchaId", registrationCaptchaId,
                "captchaCode", FIXED_CODE))), null);
        assertThat(replayed.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(replayed.body()).path("code").asInt()).isEqualTo(2010);
    }

    private JsonNode issueCaptcha(String purpose) throws Exception {
        return post("/api/v1/captcha", null, Map.of("purpose", purpose));
    }
}
