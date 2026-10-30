package me.iynotuo.judge;

import me.iynotuo.judge.judge.JudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端流程：注册 → 登录 → 上传题目 → 上传测试点 → 提交代码 → 真实编译运行判题 → 查询结果。
 * 使用 H2(MySQL 模式) + schema.sql，不需要本机 MySQL。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class ApiFlowIntegrationTest extends ApiTestSupport {

    @Autowired
    private JudgeService judgeService;

    @Test
    void userCanPublishProblemAndSolveIt() throws Exception {
        JsonNode register = post("/api/v1/auth/register", null, Map.of(
                "username", "alice",
                "email", "alice@example.com",
                "password", "password123",
                "nickname", "Alice"));
        assertThat(register.path("code").asInt()).isZero();

        JsonNode login = post("/api/v1/auth/login", null,
                Map.of("account", "alice", "password", "password123"));
        String token = login.path("data").path("token").asString();
        assertThat(token).isNotBlank();

        JsonNode created = post("/api/v1/problems", token, Map.of(
                "title", "A+B Problem",
                "difficulty", "EASY",
                "description", "计算 a+b",
                "inputFormat", "两个整数",
                "outputFormat", "一个整数",
                "timeLimitMs", 2000,
                "memoryLimitMb", 256,
                "tags", List.of("入门", "math"),
                "samples", List.of(Map.of("input", "1 2\n", "output", "3\n"))));
        long problemId = created.path("data").path("id").asLong();
        assertThat(problemId).isPositive();
        assertThat(created.path("data").path("status").asString()).isEqualTo("PUBLISHED");
        assertThat(created.path("data").path("authorNickname").asString()).isEqualTo("Alice");

        JsonNode testcases = put("/api/v1/problems/" + problemId + "/testcases", token, List.of(
                Map.of("input", "1 2\n", "output", "3\n"),
                Map.of("input", "100 -50\n", "output", "50\n")));
        assertThat(testcases.path("data").size()).isEqualTo(2);

        JsonNode list = get("/api/v1/problems", null);
        assertThat(list.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(list.path("data").path("items").get(0).path("title").asString()).isEqualTo("A+B Problem");
        assertThat(list.path("data").path("items").get(0).path("tags").size()).isEqualTo(2);

        String correctCode = """
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        System.out.println(in.nextInt() + in.nextInt());
                    }
                }
                """;
        JsonNode submission = post("/api/v1/submissions", token, Map.of(
                "problemId", problemId, "language", "JAVA", "code", correctCode));
        long submissionId = submission.path("data").path("id").asLong();
        assertThat(submission.path("data").path("status").asString()).isEqualTo("PENDING");

        // worker 抢占逻辑：队列里只有这一条，抢占后状态变为 JUDGING
        assertThat(judgeService.claimNext()).isEqualTo(submissionId);
        assertThat(judgeService.claimNext()).isNull();
        judgeService.judge(submissionId);

        JsonNode detail = get("/api/v1/submissions/" + submissionId, token);
        assertThat(detail.path("data").path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(detail.path("data").path("cases").size()).isEqualTo(2);
        assertThat(detail.path("data").path("code").asString()).contains("class Main");
        assertThat(detail.path("data").path("userNickname").asString()).isEqualTo("Alice");

        // 匿名查看：能看到结果，但看不到代码与逐点信息
        JsonNode anonymous = get("/api/v1/submissions/" + submissionId, null);
        assertThat(anonymous.path("data").path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(anonymous.path("data").path("code").isNull()).isTrue();
        assertThat(anonymous.path("data").path("cases").size()).isZero();

        // 题目详情：solved 标记与通过计数
        JsonNode problem = get("/api/v1/problems/" + problemId, token);
        assertThat(problem.path("data").path("solved").asBoolean()).isTrue();
        assertThat(problem.path("data").path("acceptedCount").asLong()).isEqualTo(1);

        // 错误答案也会被正确判出
        String wrongCode = correctCode.replace("in.nextInt() + in.nextInt()", "in.nextInt() + in.nextInt() + 1");
        long wrongSubmissionId = post("/api/v1/submissions", token, Map.of(
                "problemId", problemId, "language", "JAVA", "code", wrongCode))
                .path("data").path("id").asLong();
        judgeService.judge(wrongSubmissionId);
        JsonNode wrongDetail = get("/api/v1/submissions/" + wrongSubmissionId, token);
        assertThat(wrongDetail.path("data").path("status").asString()).isEqualTo("WRONG_ANSWER");
        assertThat(wrongDetail.path("data").path("cases").get(0).path("status").asString())
                .isEqualTo("WRONG_ANSWER");

        // 未登录不能上传题目
        assertThat(exchange(request("/api/v1/problems").POST(body(Map.of(
                "title", "x", "difficulty", "EASY", "description", "x"))), null).statusCode())
                .isEqualTo(401);
    }
}
