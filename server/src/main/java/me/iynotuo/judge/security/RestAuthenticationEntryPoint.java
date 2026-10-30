package me.iynotuo.judge.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.iynotuo.judge.common.api.JsonResponses;
import me.iynotuo.judge.common.exception.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未登录访问受保护接口时返回统一 JSON（默认会返回 403 + 空 body，不利于前端处理）。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        JsonResponses.write(response, ErrorCode.UNAUTHORIZED.status(),
                ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message());
    }
}
