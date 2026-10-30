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
 * 管理侧端到端流程：测试点增删改查、题目审核上下架、用户搜索与封禁。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-moderation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class AdminModerationIntegrationTest extends ApiTestSupport {

    @Autowired
    private UserMapper userMapper;

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

        // 管理员建题：直接发布
        JsonNode created = post("/api/v1/problems", adminToken, Map.of(
                "title", "Get Max", "difficulty", "MEDIUM", "description", "输出最大值",
                "timeLimitMs", 1000, "memoryLimitMb", 128,
                "tags", List.of("模拟", "入门")));
        long problemId = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("status").asString()).isEqualTo("PUBLISHED");

        // 测试点：单个新增、列表、详情、批量覆盖
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

        // 非作者非管理员不能查看测试点
        assertThat(exchange(request("/api/v1/problems/" + problemId + "/testcases").GET(), bobToken)
                .statusCode()).isEqualTo(403);

        // "我上传的题目"包含未发布状态，这里应为 1
        assertThat(get("/api/v1/problems/mine", adminToken).path("data").path("total").asLong())
                .isEqualTo(1);

        // 审核：下架后匿名不可见，重新上架后可见
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

        // 用户管理：搜索与封禁
        assertThat(get("/api/v1/admin/users?keyword=bob", adminToken).path("data").path("total").asLong())
                .isEqualTo(1);
        long bobId = userMapper.findByUsername("bob").getId();
        patch("/api/v1/admin/users/" + bobId + "/status", adminToken, Map.of("status", "BANNED"));

        var bannedLogin = exchange(request("/api/v1/auth/login").POST(body(Map.of(
                "account", "bob", "password", "password123"))), null);
        assertThat(bannedLogin.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(bannedLogin.body()).path("code").asInt()).isEqualTo(2004);

        // 被封禁用户的旧 token 立即失效（鉴权时以数据库状态为准）
        assertThat(exchange(request("/api/v1/admin/problems").GET(), bobToken).statusCode())
                .isEqualTo(401);
    }

    private String login(String account) throws Exception {
        return post("/api/v1/auth/login", null, Map.of("account", account, "password", "password123"))
                .path("data").path("token").asString();
    }
}
