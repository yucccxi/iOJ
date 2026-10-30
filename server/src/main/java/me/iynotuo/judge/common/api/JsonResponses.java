package me.iynotuo.judge.common.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在 Spring Security 过滤器链中（此时还没有进入 MVC 异常处理）直接写统一格式的 JSON 错误响应。
 */
public final class JsonResponses {

    private JsonResponses() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, int code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + escaped + "\",\"data\":null}");
        response.getWriter().flush();
    }
}
