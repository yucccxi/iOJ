package me.iynotuo.judge.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.iynotuo.judge.common.api.JsonResponses;
import me.iynotuo.judge.common.exception.ErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已登录但权限不足时返回统一 JSON。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        JsonResponses.write(response, ErrorCode.FORBIDDEN.status(),
                ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.message());
    }
}
