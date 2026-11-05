package me.iynotuo.judge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers token-version invalidation: a password change or "logout all devices" must make every
 * previously issued JWT unusable, while the token returned by logout-all keeps this device signed in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-token-version;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class TokenVersionIntegrationTest extends ApiTestSupport {

    private static final String OLD_PASSWORD = "password123";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Changes the password, proves old tokens are rejected, then exercises logout-all and its
     * replacement token.
     */
    @Test
    void passwordChangeAndLogoutAllInvalidateOldTokens() throws Exception {
        JsonNode registered = post("/api/v1/auth/register", null, Map.of(
                "username", "tokenuser",
                "email", "tokenuser@example.com",
                "password", OLD_PASSWORD));
        long userId = registered.path("data").path("id").asLong();

        String firstToken = login("tokenuser", OLD_PASSWORD);

        JsonNode changed = put("/api/v1/users/me/password", firstToken,
                Map.of("oldPassword", OLD_PASSWORD, "newPassword", "newpassword456"));
        assertThat(changed.path("code").asInt()).isZero();

        // The change invalidates the token that performed it, and every other token for the user.
        assertUnauthorized(getRaw("/api/v1/auth/me", firstToken));
        assertThat(JSON.readTree(exchange(request("/api/v1/auth/login").POST(body(Map.of(
                "account", "tokenuser", "password", OLD_PASSWORD))), null).body())
                .path("code").asInt()).isEqualTo(2005);

        String secondToken = login("tokenuser", "newpassword456");
        String otherDeviceToken = login("tokenuser", "newpassword456");
        assertThat(get("/api/v1/auth/me", secondToken).path("data").path("id").asLong()).isEqualTo(userId);

        JsonNode logoutAll = post("/api/v1/auth/logout-all", secondToken, Map.of());
        String replacementToken = logoutAll.path("data").path("token").asString();
        assertThat(replacementToken).isNotBlank();

        // Both pre-logout tokens are dead; the returned token keeps this device logged in.
        assertUnauthorized(getRaw("/api/v1/auth/me", secondToken));
        assertUnauthorized(getRaw("/api/v1/auth/me", otherDeviceToken));
        assertThat(get("/api/v1/auth/me", replacementToken).path("data").path("id").asLong()).isEqualTo(userId);

        int version = jdbcTemplate.queryForObject(
                "SELECT token_version FROM users WHERE id = ?", Integer.class, userId);
        assertThat(version).isEqualTo(2);
    }

    /**
     * Logs in and returns the access token.
     */
    private String login(String account, String password) throws Exception {
        return post("/api/v1/auth/login", null, Map.of("account", account, "password", password))
                .path("data").path("token").asString();
    }

    private HttpResponse<String> getRaw(String path, String token) throws Exception {
        HttpRequest.Builder builder = request(path).GET();
        return exchange(builder, token);
    }

    /**
     * Asserts that a token is no longer accepted by an authenticated endpoint.
     */
    private void assertUnauthorized(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).path("code").asInt()).isEqualTo(1002);
    }
}
