package me.iynotuo.judge;

import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.common.enums.UserStatus;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end password reset by email code: sending the code, rejecting ineligible addresses,
 * purpose isolation, single use, the attempt limit, and the resulting password change.
 *
 * <p>Users are inserted directly with a known BCrypt hash so each case can focus on the reset flow;
 * the registration flow itself is covered by {@link EmailVerificationIntegrationTest}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ioj-password-reset;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ioj.email-code.enabled=true",
                "ioj.email-code.max-attempts=3"
        })
@ActiveProfiles("test")
class PasswordResetIntegrationTest extends ApiTestSupport {

    private static final String OLD_PASSWORD = "password123";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturingEmailCodeSender mail;

    @TestConfiguration
    static class CapturingMailConfiguration {

        @Bean
        @Primary
        CapturingEmailCodeSender capturingEmailCodeSender() {
            return new CapturingEmailCodeSender();
        }
    }

    /**
     * Keeps the last code per (purpose, address) so a case can prove that a registration code is not
     * accepted by the reset endpoint.
     */
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

    /**
     * The happy path: request a code, reset with it, the old password stops working and the new one
     * works.
     */
    @Test
    void resetCodeChangesThePassword() throws Exception {
        String email = "reset-a@example.com";
        createUser("reseta", email, UserStatus.ACTIVE);
        HttpResponse<String> initialLogin = login("reseta", OLD_PASSWORD);
        assertThat(initialLogin.statusCode()).isEqualTo(200);
        String oldToken = JSON.readTree(initialLogin.body()).path("data").path("token").asString();

        JsonNode sent = sendResetCode(email);
        assertThat(sent.path("code").asInt()).isZero();
        assertThat(sent.path("data").path("expiresInSeconds").asLong()).isEqualTo(600);
        String code = mail.codeFor(EmailCodePurpose.RESET_PASSWORD, email);
        assertThat(code).matches("\\d{6}");
        assertThat(sent.toString()).doesNotContain(code);

        HttpResponse<String> reset = reset(email, code, "newpassword456");

        assertThat(reset.statusCode()).as(reset.body()).isEqualTo(200);
        assertThat(JSON.readTree(reset.body()).path("data").isNull()).isTrue();
        HttpResponse<String> oldPassword = login("reseta", OLD_PASSWORD);
        assertThat(oldPassword.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(oldPassword.body()).path("code").asInt()).isEqualTo(2005);
        // The reset invalidates every token issued before it.
        HttpResponse<String> oldTokenRequest = exchange(request("/api/v1/auth/me").GET(), oldToken);
        assertThat(oldTokenRequest.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(oldTokenRequest.body()).path("code").asInt()).isEqualTo(1002);
        assertThat(login("reseta", "newpassword456").statusCode()).isEqualTo(200);
    }

    /**
     * An address without an account is refused explicitly, so the page can tell the visitor to
     * register instead of waiting for a mail.
     */
    @Test
    void unknownAddressIsRejected() throws Exception {
        HttpResponse<String> response = requestResetCode("nobody@example.com");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(response.body()).path("code").asInt()).isEqualTo(2001);
    }

    /**
     * A banned account may not reset its password: it could not log in anyway.
     */
    @Test
    void bannedAccountIsRejected() throws Exception {
        String email = "reset-banned@example.com";
        createUser("resetbanned", email, UserStatus.BANNED);

        HttpResponse<String> response = requestResetCode(email);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(response.body()).path("code").asInt()).isEqualTo(2004);
    }

    /**
     * Codes are bound to a purpose: a code issued for registration (or one inserted directly with
     * that purpose) cannot reset a password.
     */
    @Test
    void aRegistrationCodeCannotBeUsedForReset() throws Exception {
        String email = "reset-purpose@example.com";
        createUser("resetpurpose", email, UserStatus.ACTIVE);
        // A registration code cannot be requested for an existing address, so insert one directly.
        insertCode(email, EmailCodePurpose.REGISTER, "123456");

        HttpResponse<String> reset = reset(email, "123456", "newpassword456");

        assertThat(reset.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(reset.body()).path("code").asInt()).isEqualTo(2007);
        assertThat(login("resetpurpose", OLD_PASSWORD).statusCode()).isEqualTo(200);
    }

    /**
     * A reset code is single use.
     */
    @Test
    void aUsedResetCodeCannotBeReplayed() throws Exception {
        String email = "reset-replay@example.com";
        createUser("resetreplay", email, UserStatus.ACTIVE);
        sendResetCode(email);
        String code = mail.codeFor(EmailCodePurpose.RESET_PASSWORD, email);
        assertThat(reset(email, code, "newpassword456").statusCode()).isEqualTo(200);

        HttpResponse<String> replay = reset(email, code, "anotherpass789");

        assertThat(replay.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(replay.body()).path("code").asInt()).isEqualTo(2007);
        assertThat(JSON.readTree(replay.body()).path("message").asString()).contains("已被使用");
    }

    /**
     * Reaching the attempt limit invalidates the code, so even the right code is refused afterwards.
     */
    @Test
    void theAttemptLimitInvalidatesTheResetCode() throws Exception {
        String email = "reset-attempts@example.com";
        createUser("resetattempts", email, UserStatus.ACTIVE);
        sendResetCode(email);
        String code = mail.codeFor(EmailCodePurpose.RESET_PASSWORD, email);
        String wrong = otherCode(code);

        // max-attempts is set to 3 for this context.
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpResponse<String> rejected = reset(email, wrong, "newpassword456");
            assertThat(rejected.statusCode()).isEqualTo(400);
            assertThat(JSON.readTree(rejected.body()).path("code").asInt()).isEqualTo(2007);
        }

        HttpResponse<String> lateButCorrect = reset(email, code, "newpassword456");

        assertThat(lateButCorrect.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(lateButCorrect.body()).path("message").asString()).contains("次数过多");
    }

    /**
     * The new password obeys the same rules as the registration password.
     */
    @Test
    void aTooShortNewPasswordIsRejected() throws Exception {
        String email = "reset-short@example.com";
        createUser("resetshort", email, UserStatus.ACTIVE);
        sendResetCode(email);
        String code = mail.codeFor(EmailCodePurpose.RESET_PASSWORD, email);

        HttpResponse<String> response = reset(email, code, "short");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).path("code").asInt()).isEqualTo(1001);
        // The rejected request must not consume the code.
        assertThat(reset(email, code, "longenough123").statusCode()).isEqualTo(200);
    }

    private void createUser(String username, String email, UserStatus status) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        INSERT INTO users (username, email, password_hash, nickname, role, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'USER', ?, ?, ?)
                        """,
                username, email, passwordEncoder.encode(OLD_PASSWORD), username, status.name(),
                Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    private void insertCode(String email, EmailCodePurpose purpose, String code) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        INSERT INTO email_verification_codes (email, purpose, code, attempts, expires_at, created_at)
                        VALUES (?, ?, ?, 0, ?, ?)
                        """,
                email, purpose.name(), code,
                Timestamp.valueOf(now.plusMinutes(10)), Timestamp.valueOf(now));
    }

    private JsonNode sendResetCode(String email) throws Exception {
        return post("/api/v1/auth/password-reset-codes", null, Map.of("email", email));
    }

    private HttpResponse<String> requestResetCode(String email) throws Exception {
        return exchange(request("/api/v1/auth/password-reset-codes")
                .POST(body(Map.of("email", email))), null);
    }

    private HttpResponse<String> reset(String email, String emailCode, String newPassword) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("emailCode", emailCode);
        payload.put("newPassword", newPassword);
        return exchange(request("/api/v1/auth/password-resets").POST(body(payload)), null);
    }

    private HttpResponse<String> login(String account, String password) throws Exception {
        return exchange(request("/api/v1/auth/login")
                .POST(body(Map.of("account", account, "password", password))), null);
    }

    /**
     * A 6-digit code that differs from the real one, so a wrong-code case can never pass by luck.
     */
    private String otherCode(String code) {
        return "000000".equals(code) ? "111111" : "000000";
    }
}
