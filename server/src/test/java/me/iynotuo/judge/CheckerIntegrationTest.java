package me.iynotuo.judge;

import me.iynotuo.judge.judge.JudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Custom checker (special judge) contract: who may read and write the source, how the problem detail
 * exposes it, and what the judge reports when the configured engine cannot run author-supplied code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-checker;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class CheckerIntegrationTest extends ApiTestSupport {

    /**
     * A minimal C-style checker; the local engine never compiles it, so only its text matters here.
     */
    private static final String CHECKER = """
            #include <cstdio>

            int main() {
                int a = 0, b = 0;
                scanf("%d %d", &a, &b);
                printf("checked %d", a + b);
                return 0;
            }
            """;

    @Autowired
    private JudgeService judgeService;

    /**
     * Covers the whole checker lifecycle: uploading it, keeping the source away from contestants,
     * clearing it again, and the CHECKER_ERROR verdict produced by the local engine.
     */
    @Test
    void checkerIsAuthorOnlyAndIsJudgedAsCheckerErrorOnTheLocalEngine() throws Exception {
        String authorToken = register("bob", "bob@example.com", "Bob");
        String otherToken = register("carol", "carol@example.com", "Carol");

        long problemId = createProblem(authorToken);
        JsonNode testcases = put("/api/v1/problems/" + problemId + "/testcases", authorToken,
                List.of(Map.of("input", "1 2\n", "output", "3\n")));
        assertThat(testcases.path("data").size()).isEqualTo(1);

        // A problem without a checker reports hasChecker=false
        JsonNode before = get("/api/v1/problems/" + problemId, authorToken);
        assertThat(before.path("data").path("hasChecker").asBoolean()).isFalse();

        JsonNode uploaded = put("/api/v1/problems/" + problemId + "/checker", authorToken,
                Map.of("code", CHECKER));
        assertThat(uploaded.path("code").asInt()).isZero();

        // The public detail only exposes the flag; the source itself must never appear in it
        JsonNode after = get("/api/v1/problems/" + problemId, authorToken);
        assertThat(after.path("data").path("hasChecker").asBoolean()).isTrue();
        assertThat(after.toString()).doesNotContain("int main");

        JsonNode fetched = get("/api/v1/problems/" + problemId + "/checker", authorToken);
        assertThat(fetched.path("data").path("code").asString()).contains("int main");

        // Anonymous callers and unrelated users are refused
        assertThat(exchange(request("/api/v1/problems/" + problemId + "/checker").GET(), null).statusCode())
                .isEqualTo(401);
        HttpResponse<String> forbiddenWrite = exchange(request("/api/v1/problems/" + problemId + "/checker")
                .PUT(body(Map.of("code", CHECKER))), otherToken);
        assertThat(forbiddenWrite.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(forbiddenWrite.body()).path("code").asInt()).isEqualTo(1003);
        HttpResponse<String> forbiddenRead = exchange(
                request("/api/v1/problems/" + problemId + "/checker").GET(), otherToken);
        assertThat(forbiddenRead.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(forbiddenRead.body()).path("code").asInt()).isEqualTo(1003);

        // The test profile forces engine=local, which reports a checker problem as a system-side failure
        long submissionId = post("/api/v1/submissions", otherToken, Map.of(
                "problemId", problemId, "language", "JAVA", "code", acceptedProgram()))
                .path("data").path("id").asLong();
        judgeService.judge(submissionId);

        JsonNode judged = get("/api/v1/submissions/" + submissionId, otherToken);
        assertThat(judged.path("data").path("status").asString()).isEqualTo("CHECKER_ERROR");
        assertThat(judged.path("data").path("judgeMessage").asString()).contains("isolate engine");

        // Clearing the source restores the built-in comparison
        put("/api/v1/problems/" + problemId + "/checker", authorToken, Map.of("code", ""));
        assertThat(get("/api/v1/problems/" + problemId, authorToken).path("data").path("hasChecker").asBoolean())
                .isFalse();
        assertThat(get("/api/v1/problems/" + problemId + "/checker", authorToken)
                .path("data").path("code").asString()).isEmpty();
    }

    /**
     * Registers and logs in a user.
     *
     * @param username login name
     * @param email    unique email
     * @param nickname display name
     * @return the JWT of the new user
     */
    private String register(String username, String email, String nickname) throws Exception {
        assertThat(post("/api/v1/auth/register", null, Map.of(
                "username", username, "email", email, "password", "password123", "nickname", nickname))
                .path("code").asInt()).isZero();
        JsonNode login = post("/api/v1/auth/login", null,
                Map.of("account", username, "password", "password123"));
        return login.path("data").path("token").asString();
    }

    /**
     * Creates a published problem owned by the given user.
     *
     * @param token author token
     * @return the new problem id
     */
    private long createProblem(String token) throws Exception {
        JsonNode created = post("/api/v1/problems", token, Map.of(
                "title", "Special Judge Problem",
                "difficulty", "EASY",
                "description", "输出 1 2 的和",
                "timeLimitMs", 2000,
                "memoryLimitMb", 256));
        assertThat(created.path("code").asInt()).as(created.toString()).isZero();
        long problemId = created.path("data").path("id").asLong();
        assertThat(problemId).as(created.toString()).isPositive();
        return problemId;
    }

    /**
     * Returns a correct Java solution for the test data created above.
     *
     * @return Java source code
     */
    private String acceptedProgram() {
        return """
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        System.out.println(in.nextInt() + in.nextInt());
                    }
                }
                """;
    }
}
