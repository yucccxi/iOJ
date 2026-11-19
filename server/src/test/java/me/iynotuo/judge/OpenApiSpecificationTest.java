package me.iynotuo.judge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes the OpenAPI document consumed by the frontend's openapi-typescript code generation.
 *
 * <p>The test uses the H2 test profile so schema generation does not need a MySQL instance. The
 * generated file is written to {@code target/openapi.json}; CI compares it with the committed copy
 * under {@code openapi/ioj-api-v1.json}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ioj-openapi;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "springdoc.api-docs.enabled=true"
        })
@ActiveProfiles("test")
class OpenApiSpecificationTest extends ApiTestSupport {

    @Test
    void writesOpenApiSpecification() throws Exception {
        HttpResponse<String> response = exchange(request("/v3/api-docs").GET(), null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode spec = JSON.readTree(response.body());
        assertThat(spec.path("info").path("title").asString()).isEqualTo("iOJ API");
        assertThat(spec.path("paths").has("/api/v1/catalog")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/problems/{id}")).isTrue();
        assertThat(spec.path("paths").has("/internal/judge/config")).isFalse();
        assertThat(spec.has("security")).isFalse();
        assertThat(spec.path("paths").path("/api/v1/auth/login").path("post").has("security")).isFalse();
        assertThat(spec.path("paths").path("/api/v1/auth/login").path("post")
                .path("responses").has("default")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/auth/login").path("post")
                .path("responses").has("400")).isFalse();

        Path output = Path.of("target", "openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, response.body(), StandardCharsets.UTF_8);

        Path committed = Path.of("..", "openapi", "ioj-api-v1.json").toAbsolutePath().normalize();
        if (!Files.exists(committed)) {
            committed = Path.of("openapi", "ioj-api-v1.json").toAbsolutePath().normalize();
        }
        assertThat(committed).as("committed OpenAPI schema").exists();
        String actual = response.body().replace("\r\n", "\n").trim();
        String expected = Files.readString(committed, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .trim();
        assertThat(actual)
                .as("OpenAPI schema drift; copy server/target/openapi.json to openapi/ioj-api-v1.json")
                .isEqualTo(expected);
    }
}
