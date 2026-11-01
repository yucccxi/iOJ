package me.iynotuo.judge;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the administrator-managed dictionaries for tags and problem sources: who may write them,
 * duplicate handling, usage counts, rename propagation, delete protection and the "pick an existing
 * id" contract used by the problem editor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-admin-meta;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class AdminMetaIntegrationTest extends ApiTestSupport {

    @Autowired
    private UserMapper userMapper;

    /**
     * Exercises the tag/source admin endpoints end to end and verifies the effects on a problem.
     */
    @Test
    void adminManagesTagAndSourceDictionaries() throws Exception {
        post("/api/v1/auth/register", null, Map.of(
                "username", "metaadmin", "email", "metaadmin@example.com", "password", "password123"));
        post("/api/v1/auth/register", null, Map.of(
                "username", "metauser", "email", "metauser@example.com", "password", "password123"));
        User admin = userMapper.findByUsername("metaadmin");
        userMapper.updateRole(admin.getId(), UserRole.ADMIN, LocalDateTime.now());

        String adminToken = login("metaadmin");
        String userToken = login("metauser");

        // Public dropdowns are readable by anyone and start empty
        assertThat(get("/api/v1/meta/tags", null).path("data").size()).isZero();
        assertThat(get("/api/v1/meta/sources", null).path("data").size()).isZero();

        // Only administrators may write the dictionaries
        HttpResponse<String> forbiddenTags = exchange(request("/api/v1/admin/tags")
                .POST(body(Map.of("name", "越权标签"))), userToken);
        assertThat(forbiddenTags.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(forbiddenTags.body()).path("code").asInt()).isEqualTo(1003);
        assertThat(exchange(request("/api/v1/admin/sources")
                .POST(body(Map.of("name", "越权来源"))), userToken).statusCode()).isEqualTo(403);

        // Create a tag and a source
        JsonNode tag = post("/api/v1/admin/tags", adminToken, Map.of("name", "数学"));
        long tagId = tag.path("data").path("id").asLong();
        assertThat(tagId).isPositive();
        assertThat(tag.path("data").path("problemCount").asLong()).isZero();

        JsonNode source = post("/api/v1/admin/sources", adminToken, Map.of("name", "洛谷"));
        long sourceId = source.path("data").path("id").asLong();
        assertThat(sourceId).isPositive();
        assertThat(source.path("data").path("name").asString()).isEqualTo("洛谷");

        // Duplicate names are conflicts, and the entries appear on the public dropdowns
        assertThat(exchange(request("/api/v1/admin/tags").POST(body(Map.of("name", "数学"))), adminToken)
                .statusCode()).isEqualTo(409);
        assertThat(exchange(request("/api/v1/admin/sources").POST(body(Map.of("name", "洛谷"))), adminToken)
                .statusCode()).isEqualTo(409);
        assertThat(get("/api/v1/meta/tags", null).path("data").get(0).path("id").asLong()).isEqualTo(tagId);
        assertThat(get("/api/v1/meta/sources", null).path("data").get(0).path("id").asLong())
                .isEqualTo(sourceId);

        // A problem references them by id only
        JsonNode created = post("/api/v1/problems", adminToken, Map.of(
                "title", "Meta Problem",
                "difficulty", "EASY",
                "description", "标签与来源来自字典",
                "tagIds", List.of(tagId),
                "sourceId", sourceId));
        long problemId = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("sourceName").asString()).isEqualTo("洛谷");
        assertThat(created.path("data").path("tags").size()).isEqualTo(1);

        // An unknown tag id is rejected instead of being created implicitly
        HttpResponse<String> unknownTag = exchange(request("/api/v1/problems").POST(body(Map.of(
                "title", "Bad Tag",
                "difficulty", "EASY",
                "description", "不存在的标签",
                "tagIds", List.of(999_999L)))), adminToken);
        assertThat(unknownTag.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(unknownTag.body()).path("message").asString()).contains("标签");

        // Usage counts are public now: the meta endpoints expose them
        assertThat(get("/api/v1/meta/tags", null).path("data").get(0).path("problemCount").asLong())
                .isEqualTo(1);
        assertThat(get("/api/v1/meta/sources", null).path("data").get(0).path("problemCount").asLong())
                .isEqualTo(1);

        // In-use entries cannot be deleted without an explicit force
        assertThat(exchange(request("/api/v1/admin/tags/" + tagId).DELETE(), adminToken).statusCode())
                .isEqualTo(409);
        HttpResponse<String> sourceInUse = exchange(request("/api/v1/admin/sources/" + sourceId).DELETE(), adminToken);
        assertThat(sourceInUse.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(sourceInUse.body()).path("message").asString()).contains("仍被");

        // Renames are reflected in the problem detail immediately
        put("/api/v1/admin/tags/" + tagId, adminToken, Map.of("name", "图论"));
        put("/api/v1/admin/sources/" + sourceId, adminToken, Map.of("name", "Codeforces"));
        JsonNode detail = get("/api/v1/problems/" + problemId, null);
        assertThat(detail.path("data").path("tags").get(0).path("name").asString()).isEqualTo("图论");
        assertThat(detail.path("data").path("sourceName").asString()).isEqualTo("Codeforces");

        // force=true detaches the tag from every problem and then deletes it
        assertThat(exchange(request("/api/v1/admin/tags/" + tagId + "?force=true").DELETE(), adminToken)
                .statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/problems/" + problemId, null).path("data").path("tags").size()).isZero();
        assertThat(get("/api/v1/meta/tags", null).path("data").size()).isZero();

        // The source is still used by the (renamed) problem, so it stays protected
        assertThat(exchange(request("/api/v1/admin/sources/" + sourceId).DELETE(), adminToken)
                .statusCode()).isEqualTo(409);
    }

    /**
     * Logs in with the shared test password and returns the access token.
     */
    private String login(String account) throws Exception {
        return post("/api/v1/auth/login", null, Map.of("account", account, "password", "password123"))
                .path("data").path("token").asString();
    }
}
