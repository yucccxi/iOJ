package me.iynotuo.judge;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the administrator-managed dictionaries for tags and problem scopes: who may write them,
 * duplicate handling, usage counts, rename propagation, delete protection and the "pick an existing
 * id" contract used by the problem editor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-admin-meta;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class AdminMetaIntegrationTest extends ApiTestSupport {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Exercises the tag/scope admin endpoints end to end and verifies the effects on a problem.
     */
    @Test
    void adminManagesTagAndScopeDictionaries() throws Exception {
        post("/api/v1/auth/register", null, Map.of(
                "username", "metaadmin", "email", "metaadmin@example.com", "password", "password123"));
        post("/api/v1/auth/register", null, Map.of(
                "username", "metauser", "email", "metauser@example.com", "password", "password123"));
        User admin = userMapper.findByUsername("metaadmin");
        userMapper.updateRole(admin.getId(), UserRole.ADMIN, LocalDateTime.now());

        String adminToken = login("metaadmin");
        String userToken = login("metauser");

        // Public dropdowns are readable by anyone. The scope list starts with the reserved LOCAL row.
        assertThat(get("/api/v1/meta/tags", null).path("data").size()).isZero();
        JsonNode initialScopes = get("/api/v1/meta/scopes", null).path("data");
        assertThat(initialScopes.size()).isEqualTo(1);
        assertThat(initialScopes.get(0).path("name").asString()).isEqualTo("LOCAL");
        long localScopeId = initialScopes.get(0).path("id").asLong();

        // Only administrators may write the dictionaries
        HttpResponse<String> forbiddenTags = exchange(request("/api/v1/admin/tags")
                .POST(body(Map.of("name", "越权标签"))), userToken);
        assertThat(forbiddenTags.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(forbiddenTags.body()).path("code").asInt()).isEqualTo(1003);
        assertThat(exchange(request("/api/v1/admin/scopes")
                .POST(body(Map.of("name", "越权来源"))), userToken).statusCode()).isEqualTo(403);

        // Create a tag and a scope
        JsonNode tag = post("/api/v1/admin/tags", adminToken, Map.of("name", "数学"));
        long tagId = tag.path("data").path("id").asLong();
        assertThat(tagId).isPositive();
        assertThat(tag.path("data").path("problemCount").asLong()).isZero();

        JsonNode scope = post("/api/v1/admin/scopes", adminToken, Map.of("name", "洛谷"));
        long scopeId = scope.path("data").path("id").asLong();
        assertThat(scopeId).isPositive();
        assertThat(scope.path("data").path("name").asString()).isEqualTo("洛谷");

        // Duplicate names are conflicts, and the entries appear on the public dropdowns
        assertThat(exchange(request("/api/v1/admin/tags").POST(body(Map.of("name", "数学"))), adminToken)
                .statusCode()).isEqualTo(409);
        assertThat(exchange(request("/api/v1/admin/scopes").POST(body(Map.of("name", "洛谷"))), adminToken)
                .statusCode()).isEqualTo(409);
        assertThat(get("/api/v1/meta/tags", null).path("data").get(0).path("id").asLong()).isEqualTo(tagId);
        assertThat(findScopeByName(get("/api/v1/meta/scopes", null).path("data"), "洛谷")
                .path("id").asLong()).isEqualTo(scopeId);

        // LOCAL is reserved: it cannot be renamed or deleted
        assertThat(exchange(request("/api/v1/admin/scopes/" + localScopeId)
                .PUT(body(Map.of("name", "Renamed Local"))), adminToken).statusCode()).isEqualTo(409);
        assertThat(exchange(request("/api/v1/admin/scopes/" + localScopeId).DELETE(), adminToken)
                .statusCode()).isEqualTo(409);

        // A problem references tags by id; the scope field is gone and LOCAL is assigned automatically
        JsonNode created = post("/api/v1/problems", adminToken, Map.of(
                "title", "Meta Problem",
                "difficulty", "EASY",
                "description", "标签来自字典，来源固定为 LOCAL",
                "tagIds", List.of(tagId),
                "scopeId", scopeId));
        long problemId = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("scopeId").asLong()).isEqualTo(localScopeId);
        assertThat(created.path("data").path("scopeName").asString()).isEqualTo("LOCAL");
        assertThat(created.path("data").path("tags").size()).isEqualTo(1);
        // Unrated problems expose rating -1 instead of null in every response
        assertThat(created.path("data").path("rating").asInt()).isEqualTo(-1);
        assertThat(get("/api/v1/problems/mine", adminToken)
                .path("data").path("items").get(0).path("rating").asInt()).isEqualTo(-1);
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId, null)
                .path("data").path("items").get(0).path("rating").asInt()).isEqualTo(-1);

        // An unknown tag id is rejected instead of being created implicitly
        HttpResponse<String> unknownTag = exchange(request("/api/v1/problems").POST(body(Map.of(
                "title", "Bad Tag",
                "difficulty", "EASY",
                "description", "不存在的标签",
                "tagIds", List.of(999_999L)))), adminToken);
        assertThat(unknownTag.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(unknownTag.body()).path("message").asString()).contains("标签");

        // Usage counts are public now: the meta endpoints expose them. The problem uses LOCAL, not 洛谷.
        assertThat(get("/api/v1/meta/tags", null).path("data").get(0).path("problemCount").asLong())
                .isEqualTo(1);
        assertThat(findScopeByName(get("/api/v1/meta/scopes", null).path("data"), "洛谷")
                .path("problemCount").asLong()).isZero();
        assertThat(findScopeByName(get("/api/v1/meta/scopes", null).path("data"), "LOCAL")
                .path("problemCount").asLong()).isEqualTo(1);

        // Drafts are not part of the public counts, but admin responses and delete protection include them
        jdbcTemplate.update("INSERT INTO problems "
                        + "(title, difficulty, description, author_id, status, scope_id, time_limit_ms, "
                        + "memory_limit_mb, submit_count, accepted_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "Draft Problem", "EASY", "草稿题不计入公开 problemCount", admin.getId(), "DRAFT",
                localScopeId, 1000, 256, 0, 0, LocalDateTime.now(), LocalDateTime.now());
        Long draftProblemId = jdbcTemplate.queryForObject(
                "SELECT id FROM problems WHERE title = ?", Long.class, "Draft Problem");
        jdbcTemplate.update("INSERT INTO problem_tags (problem_id, tag_id) VALUES (?, ?)",
                draftProblemId, tagId);
        assertThat(findScopeByName(get("/api/v1/meta/scopes", null).path("data"), "LOCAL")
                .path("problemCount").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/meta/tags", null).path("data").get(0).path("problemCount").asLong())
                .isEqualTo(1);

        // A draft under 洛谷 verifies that admin responses count every status while /meta does not
        jdbcTemplate.update("INSERT INTO problems "
                        + "(title, difficulty, description, author_id, status, scope_id, time_limit_ms, "
                        + "memory_limit_mb, submit_count, accepted_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "Draft Scope Problem", "EASY", "草稿题也不计入公开 problemCount", admin.getId(), "DRAFT",
                scopeId, 1000, 256, 0, 0, LocalDateTime.now(), LocalDateTime.now());

        // Scopes are still used by external OJ links; add one so rename/delete behaviour can be tested
        String externalUrl = "https://example.com/scope-rename";
        jdbcTemplate.update("INSERT INTO external_problems "
                        + "(scope_id, code, title, rating, difficulty, submit_count, accepted_count, url, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scopeId, "TEST_1", "Scope Rename", 800, "EASY", 0, 0, externalUrl,
                LocalDateTime.now(), LocalDateTime.now());
        // The public count sees the external link only; the draft under 洛谷 is still hidden
        assertThat(findScopeByName(get("/api/v1/meta/scopes", null).path("data"), "洛谷")
                .path("problemCount").asLong()).isEqualTo(1);

        // In-use entries cannot be deleted without an explicit force
        assertThat(exchange(request("/api/v1/admin/tags/" + tagId).DELETE(), adminToken).statusCode())
                .isEqualTo(409);
        HttpResponse<String> scopeInUse = exchange(request("/api/v1/admin/scopes/" + scopeId).DELETE(), adminToken);
        assertThat(scopeInUse.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(scopeInUse.body()).path("message").asString()).contains("仍被");

        // Renames are reflected in problem details and external catalog rows immediately
        JsonNode renamedTag = put("/api/v1/admin/tags/" + tagId, adminToken, Map.of("name", "图论"));
        // Admin responses count every local status: one published + one draft problem
        assertThat(renamedTag.path("data").path("problemCount").asLong()).isEqualTo(2);
        JsonNode renamedScope = put("/api/v1/admin/scopes/" + scopeId, adminToken, Map.of("name", "Codeforces"));
        // Admin response counts one draft local problem + one external link
        assertThat(renamedScope.path("data").path("problemCount").asLong()).isEqualTo(2);
        JsonNode detail = get("/api/v1/problems/" + problemId, null);
        assertThat(detail.path("data").path("tags").get(0).path("name").asString()).isEqualTo("图论");
        // The local problem keeps LOCAL; only external rows use the renamed scope
        assertThat(detail.path("data").path("scopeName").asString()).isEqualTo("LOCAL");
        assertThat(get("/api/v1/catalog?scopeId=" + scopeId, null)
                .path("data").path("items").get(0).path("scopeName").asString()).isEqualTo("Codeforces");

        // force=true detaches the tag from every problem and then deletes it
        assertThat(exchange(request("/api/v1/admin/tags/" + tagId + "?force=true").DELETE(), adminToken)
                .statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/problems/" + problemId, null).path("data").path("tags").size()).isZero();
        assertThat(get("/api/v1/meta/tags", null).path("data").size()).isZero();

        // The scope is still used by the external row, so it stays protected until that row is gone
        assertThat(exchange(request("/api/v1/admin/scopes/" + scopeId).DELETE(), adminToken)
                .statusCode()).isEqualTo(409);
        jdbcTemplate.update("DELETE FROM problems WHERE title = ?", "Draft Scope Problem");
        jdbcTemplate.update("DELETE FROM external_problems WHERE url = ?", externalUrl);
        assertThat(exchange(request("/api/v1/admin/scopes/" + scopeId).DELETE(), adminToken)
                .statusCode()).isEqualTo(200);
    }

    /**
     * Finds a scope entry by name in a {@code GET /meta/scopes} payload.
     */
    private JsonNode findScopeByName(JsonNode scopes, String name) {
        for (JsonNode scope : scopes) {
            if (name.equals(scope.path("name").asString())) {
                return scope;
            }
        }
        throw new AssertionError("scope not found: " + name);
    }

    /**
     * Logs in with the shared test password and returns the access token.
     */
    private String login(String account) throws Exception {
        return post("/api/v1/auth/login", null, Map.of("account", account, "password", "password123"))
                .path("data").path("token").asString();
    }
}
