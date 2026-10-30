package me.iynotuo.judge;

import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试的 HTTP 小工具：直接用 JDK HttpClient + Jackson 3，避免依赖测试专用客户端 API。
 */
abstract class ApiTestSupport {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;

    protected final HttpClient client = HttpClient.newHttpClient();

    protected JsonNode get(String path, String token) throws Exception {
        return send(request(path).GET(), token);
    }

    protected JsonNode post(String path, String token, Object body) throws Exception {
        return send(request(path).POST(body(body)), token);
    }

    protected JsonNode put(String path, String token, Object body) throws Exception {
        return send(request(path).PUT(body(body)), token);
    }

    protected JsonNode patch(String path, String token, Object body) throws Exception {
        return send(request(path).method("PATCH", body(body)), token);
    }

    /**
     * 返回原始响应，用于断言非 2xx 场景。
     */
    protected HttpResponse<String> exchange(HttpRequest.Builder builder, String token) throws Exception {
        builder.header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected JsonNode send(HttpRequest.Builder builder, String token) throws Exception {
        HttpResponse<String> response = exchange(builder, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    protected HttpRequest.BodyPublisher body(Object payload) {
        return HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload));
    }

    protected HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }
}
