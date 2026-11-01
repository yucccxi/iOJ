package me.iynotuo.judge;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end administrator flow: test case CRUD, problem review, user search and banning.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-moderation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class AdminModerationIntegrationTest extends ApiTestSupport {

    @Autowired
    private UserMapper userMapper;

    /**
     * Verifies the administrator flow: test case create, list, read and replace,
     * problem review (hide and publish), user search, and banning a user, including
     * the immediate loss of a token that was issued before the ban.
     */
    @Test
    void adminCanManageTestcasesAndModerate() throws Exception {
        post("/api/v1/auth/register", null, Map.of(
                "username", "moderator", "email", "mod@example.com", "password", "password123"));
        post("/api/v1/auth/register", null, Map.of(
                "username", "bob", "email", "bob@example.com", "password", "password123"));
        User moderator = userMapper.findByUsername("moderator");
        userMapper.updateRole(moderator.getId(), UserRole.ADMIN, LocalDateTime.now());

        String adminToken = login("moderator");
        String bobToken = login("bob");

        assertThat(get("/api/v1/meta/languages", null).path("data").size()).isEqualTo(4);
        assertThat(get("/api/v1/meta/tags", null).path("data").isArray()).isTrue();
        assertThat(get("/api/v1/meta/sources", null).path("data").isArray()).isTrue();

        // Tags come from the admin-managed dictionary: the author picks ids, never names
        long simulationTagId = post("/api/v1/admin/tags", adminToken, Map.of("name", "模拟"))
                .path("data").path("id").asLong();
        long entryTagId = post("/api/v1/admin/tags", adminToken, Map.of("name", "入门"))
                .path("data").path("id").asLong();

        // An administrator creates a problem: it is published immediately
        JsonNode created = post("/api/v1/problems", adminToken, Map.of(
                "title", "Get Max", "difficulty", "MEDIUM", "description", "输出最大值",
                "timeLimitMs", 1000, "memoryLimitMb", 128,
                "tagIds", List.of(simulationTagId, entryTagId)));
        long problemId = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("status").asString()).isEqualTo("PUBLISHED");

        // Test cases: add one, list, read one, replace all
        JsonNode added = post("/api/v1/problems/" + problemId + "/testcases", adminToken,
                Map.of("input", "1 2 3\n", "output", "3\n"));
        long firstCaseId = added.path("data").path("id").asLong();
        assertThat(get("/api/v1/problems/" + problemId + "/testcases", adminToken)
                .path("data").size()).isEqualTo(1);
        assertThat(get("/api/v1/problems/" + problemId + "/testcases/" + firstCaseId, adminToken)
                .path("data").path("input").asString()).isEqualTo("1 2 3\n");

        JsonNode replaced = put("/api/v1/problems/" + problemId + "/testcases", adminToken, List.of(
                Map.of("input", "3 1 2\n", "output", "3\n"),
                Map.of("input", "-1 -5\n", "output", "-1\n")));
        assertThat(replaced.path("data").size()).isEqualTo(2);

        // A user who is neither the author nor an administrator cannot read test cases
        assertThat(exchange(request("/api/v1/problems/" + problemId + "/testcases").GET(), bobToken)
                .statusCode()).isEqualTo(403);

        // "My problems" includes unpublished ones, so the total should be 1
        assertThat(get("/api/v1/problems/mine", adminToken).path("data").path("total").asLong())
                .isEqualTo(1);

        // Review: hidden problems are invisible to anonymous users until published again
        assertThat(get("/api/v1/admin/problems?status=PUBLISHED", adminToken)
                .path("data").path("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(patch("/api/v1/admin/problems/" + problemId + "/status", adminToken,
                Map.of("status", "HIDDEN")).path("code").asInt()).isZero();
        assertThat(exchange(request("/api/v1/problems/" + problemId).GET(), null).statusCode())
                .isEqualTo(403);
        assertThat(patch("/api/v1/admin/problems/" + problemId + "/status", adminToken,
                Map.of("status", "PUBLISHED")).path("code").asInt()).isZero();
        assertThat(get("/api/v1/problems/" + problemId, null).path("data").path("title").asString())
                .isEqualTo("Get Max");

        // User management: search and ban
        assertThat(get("/api/v1/admin/users?keyword=bob", adminToken).path("data").path("total").asLong())
                .isEqualTo(1);
        long bobId = userMapper.findByUsername("bob").getId();
        patch("/api/v1/admin/users/" + bobId + "/status", adminToken, Map.of("status", "BANNED"));

        var bannedLogin = exchange(request("/api/v1/auth/login").POST(body(Map.of(
                "account", "bob", "password", "password123"))), null);
        assertThat(bannedLogin.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(bannedLogin.body()).path("code").asInt()).isEqualTo(2004);

        // An old token of a banned user stops working immediately (the database status wins)
        assertThat(exchange(request("/api/v1/admin/problems").GET(), bobToken).statusCode())
                .isEqualTo(401);
    }

    /**
     * Logs in with the shared test password and returns the access token.
     */
    private String login(String account) throws Exception {
        return post("/api/v1/auth/login", null, Map.of("account", account, "password", "password123"))
                .path("data").path("token").asString();
    }
}
