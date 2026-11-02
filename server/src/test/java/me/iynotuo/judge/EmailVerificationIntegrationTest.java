package me.iynotuo.judge;

import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.user.mail.EmailCodeSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end email verification: code delivery, resend cooldown, the hourly cap, registration with
 * and without a code, single use, the attempt limit and expiry.
 *
 * <p>The profile disables verification for all other tests, so this class re-enables it and captures
 * the mails with a fake sender instead of talking to an SMTP server. The limits are lowered here
 * (one second cooldown, two codes per hour, three attempts) so the rules can be checked without
 * waiting for real timeouts.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ioj-email-code;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "ioj.email-code.enabled=true",
                "ioj.email-code.ttl-minutes=10",
                "ioj.email-code.resend-interval-seconds=1",
                "ioj.email-code.max-per-hour=2",
                "ioj.email-code.max-attempts=3"
        })
@ActiveProfiles("test")
class EmailVerificationIntegrationTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturingEmailCodeSender mail;

    /**
     * Replaces the SMTP sender for this context. {@code @Primary} wins over the conditionally
     * created {@link me.iynotuo.judge.user.mail.SmtpEmailCodeSender}.
     */
    @TestConfiguration
    static class CapturingMailConfiguration {

        @Bean
        @Primary
        CapturingEmailCodeSender capturingEmailCodeSender() {
            return new CapturingEmailCodeSender();
        }
    }

    /**
     * Keeps the last code per address instead of sending it.
     */
    static class CapturingEmailCodeSender implements EmailCodeSender {

        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void send(String email, String code, int ttlMinutes, EmailCodePurpose purpose) {
            codes.put(email, code);
        }

        String codeFor(String email) {
            return codes.get(email);
        }
    }

    /**
     * The response reports the lifetime and the cooldown, and the code itself only travels by mail.
     */
    @Test
    void codeIsMailedAndNeverReturnedInTheResponse() throws Exception {
        String email = "mail-a@example.com";

        JsonNode response = sendCode(email);

        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("data").path("expiresInSeconds").asLong()).isEqualTo(600);
        assertThat(response.path("data").path("resendAfterSeconds").asLong()).isEqualTo(1);
        String code = mail.codeFor(email);
        assertThat(code).matches("\\d{6}");
        assertThat(response.toString()).doesNotContain(code);
    }

    /**
     * A second request inside the cooldown is refused with 2008 instead of sending another mail.
     */
    @Test
    void resendInsideTheCooldownIsRejected() throws Exception {
        String email = "mail-b@example.com";
        assertThat(sendCode(email).path("code").asInt()).isZero();

        HttpResponse<String> second = requestCode(email);

        assertThat(second.statusCode()).isEqualTo(429);
        assertThat(JSON.readTree(second.body()).path("code").asInt()).isEqualTo(2008);
    }

    /**
     * Registration fails without a code and with a wrong one, and succeeds with the mailed code.
     */
    @Test
    void registrationRequiresTheMailedCode() throws Exception {
        String email = "mail-c@example.com";
        assertThat(sendCode(email).path("code").asInt()).isZero();
        String code = mail.codeFor(email);

        HttpResponse<String> missing = register("mailc", email, null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(missing.body()).path("code").asInt()).isEqualTo(2007);
        assertThat(JSON.readTree(missing.body()).path("message").asString()).contains("不能为空");

        HttpResponse<String> wrong = register("mailc", email, otherCode(code));
        assertThat(wrong.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(wrong.body()).path("code").asInt()).isEqualTo(2007);
        assertThat(JSON.readTree(wrong.body()).path("message").asString()).contains("还可尝试");

        HttpResponse<String> accepted = register("mailc", email, code);
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        assertThat(JSON.readTree(accepted.body()).path("data").path("id").asLong()).isPositive();
    }

    /**
     * A code is single use: after a successful registration the same code cannot create a second
     * account, even when the first account is gone.
     */
    @Test
    void aUsedCodeCannotBeReplayed() throws Exception {
        String email = "mail-d@example.com";
        assertThat(sendCode(email).path("code").asInt()).isZero();
        String code = mail.codeFor(email);
        assertThat(register("maild", email, code).statusCode()).isEqualTo(200);

        // Drop the account so the uniqueness check does not answer before the code check does.
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", "maild");

        HttpResponse<String> replay = register("maild2", email, code);

        assertThat(replay.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(replay.body()).path("code").asInt()).isEqualTo(2007);
        assertThat(JSON.readTree(replay.body()).path("message").asString()).contains("已被使用");
    }

    /**
     * Reaching the configured attempt limit invalidates the code, so the right code is refused
     * afterwards as well. The attempt counter must survive the rollback of the failed registration.
     */
    @Test
    void theAttemptLimitInvalidatesTheCode() throws Exception {
        String email = "mail-e@example.com";
        assertThat(sendCode(email).path("code").asInt()).isZero();
        String code = mail.codeFor(email);
        String wrong = otherCode(code);

        // max-attempts is set to 3 for this context.
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpResponse<String> rejected = register("maile" + attempt, email, wrong);
            assertThat(rejected.statusCode()).isEqualTo(400);
            assertThat(JSON.readTree(rejected.body()).path("code").asInt()).isEqualTo(2007);
        }

        HttpResponse<String> lateButCorrect = register("maile9", email, code);

        assertThat(lateButCorrect.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(lateButCorrect.body()).path("message").asString()).contains("次数过多");
    }

    /**
     * An expired code is refused with a message that tells the client to request a new one.
     */
    @Test
    void anExpiredCodeIsRejected() throws Exception {
        String email = "mail-f@example.com";
        assertThat(sendCode(email).path("code").asInt()).isZero();
        String code = mail.codeFor(email);

        jdbcTemplate.update("UPDATE email_verification_codes SET expires_at = ? WHERE email = ?",
                LocalDateTime.now().minusMinutes(1), email);

        HttpResponse<String> response = register("mailf", email, code);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).path("code").asInt()).isEqualTo(2007);
        assertThat(JSON.readTree(response.body()).path("message").asString()).contains("已过期");
    }

    /**
     * An address that already has an account cannot request a registration code.
     */
    @Test
    void anAlreadyRegisteredAddressCannotRequestACode() throws Exception {
        String email = "mail-g@example.com";
        assertThat(sendCode(email).path("code").asInt()).isZero();
        assertThat(register("mailg", email, mail.codeFor(email)).statusCode()).isEqualTo(200);

        HttpResponse<String> response = requestCode(email);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(response.body()).path("code").asInt()).isEqualTo(2003);
    }

    /**
     * The hourly cap is independent from the cooldown: once the quota is used up, waiting out the
     * cooldown is not enough.
     *
     * <p>The quota is filled with one real send plus one row written directly, because reaching it
     * through the endpoint would first have to wait out the cooldown. The check itself still runs
     * against the real table and the real SQL.</p>
     */
    @Test
    void theHourlyLimitIsEnforced() throws Exception {
        String email = "mail-h@example.com";
        assertThat(sendCode(email).path("code").asInt()).isZero();

        // max-per-hour is set to 2 for this context, so one more row uses the quota up.
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        INSERT INTO email_verification_codes (email, purpose, code, attempts, expires_at, created_at)
                        VALUES (?, 'REGISTER', '000000', 0, ?, ?)
                        """, email, now.plusMinutes(10), now);

        HttpResponse<String> third = requestCode(email);

        assertThat(third.statusCode()).isEqualTo(429);
        assertThat(JSON.readTree(third.body()).path("code").asInt()).isEqualTo(2008);
        assertThat(JSON.readTree(third.body()).path("message").asString()).contains("一小时内最多");
    }

    private JsonNode sendCode(String email) throws Exception {
        return post("/api/v1/auth/email-codes", null, Map.of("email", email));
    }

    private HttpResponse<String> requestCode(String email) throws Exception {
        return exchange(request("/api/v1/auth/email-codes").POST(body(Map.of("email", email))), null);
    }

    private HttpResponse<String> register(String username, String email, String emailCode) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("email", email);
        payload.put("password", "password123");
        if (emailCode != null) {
            payload.put("emailCode", emailCode);
        }
        return exchange(request("/api/v1/auth/register").POST(body(payload)), null);
    }

    /**
     * A 6-digit code that differs from the real one, so a wrong-code case can never pass by luck.
     */
    private String otherCode(String code) {
        return "000000".equals(code) ? "111111" : "000000";
    }
}
