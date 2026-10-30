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
 * Small HTTP helper for integration tests. It uses the JDK HttpClient and Jackson 3 to avoid depending on test-only client APIs.
 */
abstract class ApiTestSupport {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;

    protected final HttpClient client = HttpClient.newHttpClient();

    /**
     * Sends a GET request and returns the parsed JSON response.
     */
    protected JsonNode get(String path, String token) throws Exception {
        return send(request(path).GET(), token);
    }

    /**
     * Sends a POST request with a JSON body and returns the parsed JSON response.
     */
    protected JsonNode post(String path, String token, Object body) throws Exception {
        return send(request(path).POST(body(body)), token);
    }

    /**
     * Sends a PUT request with a JSON body and returns the parsed JSON response.
     */
    protected JsonNode put(String path, String token, Object body) throws Exception {
        return send(request(path).PUT(body(body)), token);
    }

    /**
     * Sends a PATCH request with a JSON body and returns the parsed JSON response.
     */
    protected JsonNode patch(String path, String token, Object body) throws Exception {
        return send(request(path).method("PATCH", body(body)), token);
    }

    /**
     * Returns the raw response so tests can assert on non-2xx status codes.
     */
    protected HttpResponse<String> exchange(HttpRequest.Builder builder, String token) throws Exception {
        builder.header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends the request, expects HTTP 200 and parses the JSON body.
     */
    protected JsonNode send(HttpRequest.Builder builder, String token) throws Exception {
        HttpResponse<String> response = exchange(builder, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    /**
     * Serializes the payload to JSON.
     */
    protected HttpRequest.BodyPublisher body(Object payload) {
        return HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload));
    }

    /**
     * Builds a request against the random port of the running test server.
     */
    protected HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }
}
