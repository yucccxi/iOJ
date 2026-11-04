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
        // Local problems always get the reserved LOCAL scope; uploads cannot choose one.
        assertThat(created.path("data").path("scopeName").asString()).isEqualTo("LOCAL");
        long localScopeId = created.path("data").path("scopeId").asLong();
        assertThat(localScopeId).isPositive();

        // Publishing one problem shows up as one contribution, visible to anonymous visitors too
        JsonNode profile = get("/api/v1/users/" + authorId, null);
        assertThat(profile.path("data").path("contributedCount").asLong()).isEqualTo(1);
        assertThat(profile.path("data").path("solvedCount").asLong()).isZero();

        JsonNode testcases = put("/api/v1/problems/" + problemId + "/testcases", token, List.of(
                Map.of("input", "1 2\n", "output", "3\n"),
                Map.of("input", "100 -50\n", "output", "50\n")));
        assertThat(testcases.path("data").size()).isEqualTo(2);

        // The public problem list moved to GET /catalog (this endpoint now only serves detail + CRUD)
        JsonNode list = get("/api/v1/catalog?scopeId=" + localScopeId, null);
        assertThat(list.path("data").path("total").asLong()).isEqualTo(1);
        JsonNode listItem = list.path("data").path("items").get(0);
        assertThat(listItem.path("kind").isMissingNode()).isTrue();
        assertThat(listItem.path("title").asString()).isEqualTo("A+B Problem");
        assertThat(listItem.path("scopeId").asLong()).isEqualTo(localScopeId);
        assertThat(listItem.path("scopeName").asString()).isEqualTo("LOCAL");
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
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&rating=800", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&minRating=700&maxRating=900", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&minRating=1000", null)
                .path("data").path("total").asLong()).isZero();
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&maxRating=799", null)
                .path("data").path("total").asLong()).isZero();
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&difficulty=EASY", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&difficulty=HARD", null)
                .path("data").path("total").asLong()).isZero();

        // A scopeId in the payload is ignored (the field no longer exists): local problems keep LOCAL
        JsonNode ignoredScope = post("/api/v1/problems", token, Map.of(
                "title", "Ignored Scope",
                "difficulty", "EASY",
                "description", "scopeId 字段会被忽略",
                "scopeId", 999_999L));
        long ignoredScopeProblemId = ignoredScope.path("data").path("id").asLong();
        assertThat(ignoredScope.path("data").path("scopeName").asString()).isEqualTo("LOCAL");
        assertThat(ignoredScope.path("data").path("scopeId").asLong()).isEqualTo(localScopeId);
        // Remove it again so the catalog totals below stay deterministic
        assertThat(exchange(request("/api/v1/problems/" + ignoredScopeProblemId).DELETE(), token)
                .statusCode()).isEqualTo(200);

        // UNKNOWN is reserved for external problems; locally authored problems reject it
        HttpResponse<String> badDifficulty = exchange(request("/api/v1/problems").POST(body(Map.of(
                "title", "Bad Difficulty",
                "difficulty", "UNKNOWN",
                "description", "本地题不能使用 UNKNOWN"))), token);
        assertThat(badDifficulty.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(badDifficulty.body()).path("message").asString()).contains("EASY");

        // --- Unified catalog: local problems and external OJ links in one filtered list ---
        jdbcTemplate.update("INSERT INTO scopes (name, created_at) VALUES (?, ?)",
                "Codeforces", LocalDateTime.now());
        Long scopeId = jdbcTemplate.queryForObject(
                "SELECT id FROM scopes WHERE name = ?", Long.class, "Codeforces");
        String externalUrl = "https://codeforces.com/problemset/problem/1/A";
        jdbcTemplate.update("INSERT INTO external_problems "
                        + "(scope_id, code, title, rating, difficulty, `time`, submit_count, accepted_count, url, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scopeId, "CF_1A", "Two Sum", 1200, "EASY",
                LocalDateTime.of(2020, 1, 1, 8, 0), 42, 17, externalUrl,
                LocalDateTime.now(), LocalDateTime.now());
        Long externalId = jdbcTemplate.queryForObject(
                "SELECT id FROM external_problems WHERE url = ?", Long.class, externalUrl);
        jdbcTemplate.update("INSERT INTO external_problem_tags (external_problem_id, tag_id) VALUES (?, ?)",
                externalId, mathTagId);

        // Both kinds show up, newest first
        JsonNode catalog = get("/api/v1/catalog", null);
        assertThat(catalog.path("data").path("total").asLong()).isEqualTo(2);
        assertThat(catalog.path("data").path("items").size()).isEqualTo(2);

        // Filtering by the Codeforces scope id returns only the OJ link
        JsonNode externalOnly = get("/api/v1/catalog?scopeId=" + scopeId, null);
        assertThat(externalOnly.path("data").path("total").asLong()).isEqualTo(1);
        // Repeated scope ids are OR-ed
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&scopeId=" + scopeId, null)
                .path("data").path("total").asLong()).isEqualTo(2);
        JsonNode externalItem = externalOnly.path("data").path("items").get(0);
        assertThat(externalItem.path("code").asString()).isEqualTo("CF_1A");
        assertThat(externalItem.path("time").asString()).isEqualTo("2020-01-01T08:00:00");
        assertThat(externalItem.path("url").asString()).isEqualTo(externalUrl);
        assertThat(externalItem.path("scopeName").asString()).isEqualTo("Codeforces");
        assertThat(externalItem.path("rating").asInt()).isEqualTo(1200);
        assertThat(externalItem.path("difficulty").asString()).isEqualTo("EASY");
        assertThat(get("/api/v1/catalog?scopeId=" + scopeId + "&difficulty=EASY", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?scopeId=" + scopeId + "&difficulty=HARD", null)
                .path("data").path("total").asLong()).isZero();
        assertThat(get("/api/v1/catalog?difficulty=EASY", null)
                .path("data").path("total").asLong()).isEqualTo(2);
        // Counters come from the external OJ (imported), not from local judging
        assertThat(externalItem.path("submitCount").asInt()).isEqualTo(42);
        assertThat(externalItem.path("acceptedCount").asInt()).isEqualTo(17);
        assertThat(externalItem.path("authorId").isNull()).isTrue();
        assertThat(externalItem.path("authorNickname").isNull()).isTrue();
        assertThat(externalItem.path("tags").size()).isEqualTo(1);

        // Local problems have no original-OJ code
        JsonNode localItem = get("/api/v1/catalog?scopeId=" + localScopeId, null)
                .path("data").path("items").get(0);
        assertThat(localItem.path("code").isNull()).isTrue();
        assertThat(localItem.path("time").isNull()).isTrue();

        // Title search spans both kinds
        assertThat(get("/api/v1/catalog?keyword=Sum", null).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?keyword=Problem", null).path("data").path("total").asLong()).isEqualTo(1);

        // Scope id filtering: Codeforces matches only the external row, LOCAL only the local one
        assertThat(get("/api/v1/catalog?scopeId=" + scopeId, null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId, null)
                .path("data").path("total").asLong()).isEqualTo(1);

        // The "math" tag is on both rows, so the tag filter keeps both kinds
        assertThat(get("/api/v1/catalog?tagId=" + mathTagId, null)
                .path("data").path("total").asLong()).isEqualTo(2);

        // Multiple tags: ANY is the default (local + external), ALL only matches the local row
        String bothTags = "tagId=" + entryTagId + "&tagId=" + mathTagId;
        assertThat(get("/api/v1/catalog?" + bothTags, null)
                .path("data").path("total").asLong()).isEqualTo(2);
        assertThat(get("/api/v1/catalog?" + bothTags + "&tagMatch=ANY", null)
                .path("data").path("total").asLong()).isEqualTo(2);
        assertThat(get("/api/v1/catalog?" + bothTags + "&tagMatch=ALL", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?" + bothTags + "&scopeId=" + localScopeId + "&tagMatch=ALL", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?" + bothTags + "&scopeId=" + scopeId + "&tagMatch=ALL", null)
                .path("data").path("total").asLong()).isZero();

        // Sorting: external has the higher rating/counters, local has no submissions.
        // Without a kind field, an external row is identified by its non-null url.
        assertThat(get("/api/v1/catalog?scopeId=" + scopeId + "&sort=RATING", null)
                .path("data").path("items").get(0).path("url").isNull()).isFalse();
        assertThat(get("/api/v1/catalog?sort=ACCEPTED", null)
                .path("data").path("items").get(0).path("url").isNull()).isFalse();
        assertThat(get("/api/v1/catalog?sort=SUBMISSIONS", null)
                .path("data").path("items").get(0).path("url").isNull()).isFalse();
        assertThat(get("/api/v1/catalog?sort=ACCEPTANCE_RATE", null)
                .path("data").path("items").get(0).path("url").isNull()).isFalse();
        assertThat(get("/api/v1/catalog?sort=ACCEPTANCE_RATE", null)
                .path("data").path("items").get(1).path("url").isNull()).isTrue();
        assertThat(get("/api/v1/catalog?sort=DEFAULT", null)
                .path("data").path("items").get(0).path("url").isNull()).isTrue();

        // ASC reverses the primary direction, but zero-submission rows stay last for rate
        assertThat(get("/api/v1/catalog?scopeId=" + localScopeId + "&sort=RATING&order=ASC", null)
                .path("data").path("items").get(0).path("url").isNull()).isTrue();
        assertThat(get("/api/v1/catalog?sort=ACCEPTED&order=ASC", null)
                .path("data").path("items").get(0).path("url").isNull()).isTrue();
        assertThat(get("/api/v1/catalog?sort=SUBMISSIONS&order=ASC", null)
                .path("data").path("items").get(0).path("url").isNull()).isTrue();
        assertThat(get("/api/v1/catalog?sort=ACCEPTANCE_RATE&order=ASC", null)
                .path("data").path("items").get(0).path("url").isNull()).isFalse();
        assertThat(get("/api/v1/catalog?sort=DEFAULT&order=ASC", null)
                .path("data").path("items").get(0).path("url").isNull()).isFalse();

        HttpResponse<String> badSort = exchange(request("/api/v1/catalog?sort=FOO").GET(), null);
        assertThat(badSort.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(badSort.body()).path("code").asInt()).isEqualTo(1000);
        HttpResponse<String> badOrder = exchange(request("/api/v1/catalog?order=FOO").GET(), null);
        assertThat(badOrder.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(badOrder.body()).path("code").asInt()).isEqualTo(1000);
        HttpResponse<String> ratingWithoutScope = exchange(
                request("/api/v1/catalog?sort=RATING").GET(), null);
        assertThat(ratingWithoutScope.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(ratingWithoutScope.body()).path("code").asInt()).isEqualTo(1000);
        HttpResponse<String> ratingWithMultipleScopes = exchange(
                request("/api/v1/catalog?scopeId=" + localScopeId + "&scopeId=" + scopeId + "&sort=RATING").GET(), null);
        assertThat(ratingWithMultipleScopes.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(ratingWithMultipleScopes.body()).path("code").asInt()).isEqualTo(1000);

        // Rating range keeps only the local 800, and exact rating matches one row too
        assertThat(get("/api/v1/catalog?minRating=700&maxRating=900", null)
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/catalog?rating=1200", null)
                .path("data").path("total").asLong()).isEqualTo(1);

        // An unknown scope id is a request-parameter error
        HttpResponse<String> unknownScope = exchange(request("/api/v1/catalog?scopeId=999999").GET(), null);
        assertThat(unknownScope.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(unknownScope.body()).path("code").asInt()).isEqualTo(1000);

        // tagMatch only accepts ANY / ALL
        HttpResponse<String> badTagMatch = exchange(request("/api/v1/catalog?tagMatch=FOO").GET(), null);
        assertThat(badTagMatch.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(badTagMatch.body()).path("code").asInt()).isEqualTo(1000);

        // An external problem without a rating is UNKNOWN, not EASY
        String unratedUrl = "https://codeforces.com/problemset/problem/2/A";
        jdbcTemplate.update("INSERT INTO external_problems "
                        + "(scope_id, code, title, rating, difficulty, submit_count, accepted_count, url, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scopeId, "CF_2A", "Unrated Example", null, "UNKNOWN", 0, 0, unratedUrl,
                LocalDateTime.now(), LocalDateTime.now());
        JsonNode unknownOnly = get("/api/v1/catalog?scopeId=" + scopeId + "&difficulty=UNKNOWN", null);
        assertThat(unknownOnly.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(unknownOnly.path("data").path("items").get(0).path("difficulty").asString())
                .isEqualTo("UNKNOWN");
        assertThat(unknownOnly.path("data").path("items").get(0).path("rating").asInt()).isEqualTo(-1);

        // Repeated difficulty parameters are OR-ed: local EASY, external EASY and external HARD
        String hardUrl = "https://codeforces.com/problemset/problem/3/A";
        jdbcTemplate.update("INSERT INTO external_problems "
                        + "(scope_id, code, title, rating, difficulty, submit_count, accepted_count, url, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scopeId, "CF_3A", "Hard Example", 2500, "HARD", 0, 0, hardUrl,
                LocalDateTime.now(), LocalDateTime.now());
        assertThat(get("/api/v1/catalog?difficulty=EASY&difficulty=HARD", null)
                .path("data").path("total").asLong()).isEqualTo(3);
        assertThat(get("/api/v1/catalog?difficulty=HARD", null)
                .path("data").path("total").asLong()).isEqualTo(1);

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
