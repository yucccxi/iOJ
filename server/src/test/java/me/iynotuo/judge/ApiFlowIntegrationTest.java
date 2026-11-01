package me.iynotuo.judge;

import me.iynotuo.judge.judge.JudgeService;
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
 * End-to-end flow: register, log in, create a problem, upload test cases, submit code, run the real judge and read the result.
 * Uses H2 in MySQL mode with schema.sql, so no local MySQL is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ioj-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class ApiFlowIntegrationTest extends ApiTestSupport {

    @Autowired
    private JudgeService judgeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Verifies the happy path from an anonymous visitor to an accepted submission:
     * registration, login, problem creation, test data upload, anonymous and
     * authenticated problem lookups, a real Java compile and judge run, the field
     * masking applied to anonymous callers, the solved flag and wrong-answer judging.
     */
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

        long authorId = register.path("data").path("id").asLong();
        // A brand new account has not contributed any problem yet
        assertThat(get("/api/v1/users/" + authorId, null)
                .path("data").path("contributedCount").asLong()).isZero();

        // Tags are an admin-managed dictionary now, so create them first and submit ids
        jdbcTemplate.update("INSERT INTO tags (name) VALUES (?), (?)", "入门", "math");
        Long entryTagId = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE name = ?", Long.class, "入门");
        Long mathTagId = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE name = ?", Long.class, "math");

        JsonNode created = post("/api/v1/problems", token, Map.of(
                "title", "A+B Problem",
                "difficulty", "EASY",
                "rating", 800,
                "description", "计算 a+b",
                "inputFormat", "两个整数",
                "outputFormat", "一个整数",
                "timeLimitMs", 2000,
                "memoryLimitMb", 256,
                "tagIds", List.of(entryTagId, mathTagId),
                "samples", List.of(Map.of("input", "1 2\n", "output", "3\n"))));
        long problemId = created.path("data").path("id").asLong();
        assertThat(problemId).isPositive();
        assertThat(created.path("data").path("status").asString()).isEqualTo("PUBLISHED");
        assertThat(created.path("data").path("authorNickname").asString()).isEqualTo("Alice");
        assertThat(created.path("data").path("rating").asInt()).isEqualTo(800);

        // Publishing one problem shows up as one contribution, visible to anonymous visitors too
        JsonNode profile = get("/api/v1/users/" + authorId, null);
        assertThat(profile.path("data").path("contributedCount").asLong()).isEqualTo(1);
        assertThat(profile.path("data").path("solvedCount").asLong()).isZero();

        JsonNode testcases = put("/api/v1/problems/" + problemId + "/testcases", token, List.of(
                Map.of("input", "1 2\n", "output", "3\n"),
                Map.of("input", "100 -50\n", "output", "50\n")));
        assertThat(testcases.path("data").size()).isEqualTo(2);

        // The public problem list moved to GET /catalog (this endpoint now only serves detail + CRUD)
        JsonNode list = get("/api/v1/catalog?kind=LOCAL", null);
        assertThat(list.path("data").path("total").asLong()).isEqualTo(1);
        JsonNode listItem = list.path("data").path("items").get(0);
        assertThat(listItem.path("kind").asString()).isEqualTo("LOCAL");
        assertThat(listItem.path("title").asString()).isEqualTo("A+B Problem");
        assertThat(listItem.path("tags").size()).isEqualTo(2);
        assertThat(listItem.path("rating").asInt()).isEqualTo(800);
        assertThat(listItem.path("difficulty").asString()).isEqualTo("EASY");
        assertThat(listItem.path("submitCount").isNull()).isFalse();
        assertThat(listItem.path("acceptedCount").isNull()).isFalse();
        assertThat(listItem.path("authorNickname").asString()).isEqualTo("Alice");
        assertThat(listItem.path("url").isNull()).isTrue();

        // The retired endpoint answers 405 for GET (the path still exists for POST)
        HttpResponse<String> retiredList = exchange(request("/api/v1/problems").GET(), token);
        assertThat(retiredList.statusCode()).isEqualTo(405);
        assertThat(JSON.readTree(retiredList.body()).path("code").asInt()).isEqualTo(1000);

        // Rating/difficulty filters live on the catalog now
        assertThat(get("/api/v1/catalog?kind=LOCAL&rating=800", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?kind=LOCAL&minRating=700&maxRating=900", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?kind=LOCAL&minRating=1000", null)
                .path("data").path("total").asLong()).isZero();
        assertThat(get("/api/v1/catalog?kind=LOCAL&maxRating=799", null)
                .path("data").path("total").asLong()).isZero();
        assertThat(get("/api/v1/catalog?kind=LOCAL&difficulty=EASY", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?kind=LOCAL&difficulty=HARD", null)
                .path("data").path("total").asLong()).isZero();

        // Sources are admin-managed: an unknown sourceId is rejected before hitting the foreign key
        HttpResponse<String> badSource = exchange(request("/api/v1/problems").POST(body(Map.of(
                "title", "Bad Source",
                "difficulty", "EASY",
                "description", "来源不存在时应当报错",
                "sourceId", 999_999L))), token);
        assertThat(badSource.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(badSource.body()).path("message").asString()).contains("来源");

        // --- Unified catalog: local problems and external OJ links in one filtered list ---
        jdbcTemplate.update("INSERT INTO sources (name, created_at) VALUES (?, ?)",
                "Codeforces", LocalDateTime.now());
        Long sourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM sources WHERE name = ?", Long.class, "Codeforces");
        String externalUrl = "https://codeforces.com/problemset/problem/1/A";
        jdbcTemplate.update("INSERT INTO external_problems (source_id, title, rating, url, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                sourceId, "Two Sum", 1200, externalUrl, LocalDateTime.now(), LocalDateTime.now());
        Long externalId = jdbcTemplate.queryForObject(
                "SELECT id FROM external_problems WHERE url = ?", Long.class, externalUrl);
        jdbcTemplate.update("INSERT INTO external_problem_tags (external_problem_id, tag_id) VALUES (?, ?)",
                externalId, mathTagId);

        // Both kinds show up, newest first
        JsonNode catalog = get("/api/v1/catalog", null);
        assertThat(catalog.path("data").path("total").asLong()).isEqualTo(2);
        assertThat(catalog.path("data").path("items").size()).isEqualTo(2);

        // kind=EXTERNAL returns only the OJ link, with url and sourceName filled in
        JsonNode externalOnly = get("/api/v1/catalog?kind=EXTERNAL", null);
        assertThat(externalOnly.path("data").path("total").asLong()).isEqualTo(1);
        JsonNode externalItem = externalOnly.path("data").path("items").get(0);
        assertThat(externalItem.path("kind").asString()).isEqualTo("EXTERNAL");
        assertThat(externalItem.path("url").asString()).isEqualTo(externalUrl);
        assertThat(externalItem.path("sourceName").asString()).isEqualTo("Codeforces");
        assertThat(externalItem.path("rating").asInt()).isEqualTo(1200);
        assertThat(externalItem.path("difficulty").isNull()).isTrue();
        assertThat(externalItem.path("submitCount").isNull()).isTrue();
        assertThat(externalItem.path("authorNickname").isNull()).isTrue();
        assertThat(externalItem.path("tags").size()).isEqualTo(1);

        // Title search spans both kinds
        assertThat(get("/api/v1/catalog?keyword=Sum", null).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?keyword=Problem", null).path("data").path("total").asLong()).isEqualTo(1);

        // Source filter only matches the external row: the local problem has no source
        assertThat(get("/api/v1/catalog?sourceId=" + sourceId, null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?kind=LOCAL&sourceId=" + sourceId, null)
                .path("data").path("total").asLong()).isZero();

        // The "math" tag is on both rows, so the tag filter keeps both kinds
        assertThat(get("/api/v1/catalog?tagId=" + mathTagId, null)
                .path("data").path("total").asLong()).isEqualTo(2);

        // Rating range keeps only the local 800, and exact rating matches one row too
        assertThat(get("/api/v1/catalog?minRating=700&maxRating=900", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?rating=1200", null)
                .path("data").path("total").asLong()).isEqualTo(1);

        // An unknown kind is a parameter error, not an empty page
        HttpResponse<String> badKind = exchange(request("/api/v1/catalog?kind=FOO").GET(), null);
        assertThat(badKind.statusCode()).isEqualTo(400);

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

        // Worker claim logic: this is the only queued submission, so claiming it moves it to JUDGING
        assertThat(judgeService.claimNext()).isEqualTo(submissionId);
        assertThat(judgeService.claimNext()).isNull();
        judgeService.judge(submissionId);

        JsonNode detail = get("/api/v1/submissions/" + submissionId, token);
        assertThat(detail.path("data").path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(detail.path("data").path("cases").size()).isEqualTo(2);
        assertThat(detail.path("data").path("code").asString()).contains("class Main");
        assertThat(detail.path("data").path("userNickname").asString()).isEqualTo("Alice");

        // Anonymous view: the result is visible, but the source code and per-case details are not
        JsonNode anonymous = get("/api/v1/submissions/" + submissionId, null);
        assertThat(anonymous.path("data").path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(anonymous.path("data").path("code").isNull()).isTrue();
        assertThat(anonymous.path("data").path("cases").size()).isZero();

        // Problem detail: the solved flag and accepted count
        JsonNode problem = get("/api/v1/problems/" + problemId, token);
        assertThat(problem.path("data").path("solved").asBoolean()).isTrue();
        assertThat(problem.path("data").path("acceptedCount").asLong()).isEqualTo(1);

        // A wrong answer is detected as WRONG_ANSWER
        String wrongCode = correctCode.replace("in.nextInt() + in.nextInt()", "in.nextInt() + in.nextInt() + 1");
        long wrongSubmissionId = post("/api/v1/submissions", token, Map.of(
                "problemId", problemId, "language", "JAVA", "code", wrongCode))
                .path("data").path("id").asLong();
        judgeService.judge(wrongSubmissionId);
        JsonNode wrongDetail = get("/api/v1/submissions/" + wrongSubmissionId, token);
        assertThat(wrongDetail.path("data").path("status").asString()).isEqualTo("WRONG_ANSWER");
        assertThat(wrongDetail.path("data").path("cases").get(0).path("status").asString())
                .isEqualTo("WRONG_ANSWER");

        // Anonymous users cannot create problems
        assertThat(exchange(request("/api/v1/problems").POST(body(Map.of(
                "title", "x", "difficulty", "EASY", "description", "x"))), null).statusCode())
                .isEqualTo(401);
    }
}
