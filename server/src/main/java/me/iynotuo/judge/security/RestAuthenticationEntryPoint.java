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
 * Returns unified JSON when an unauthenticated user hits a protected endpoint (the default 403 with an empty body is hard for clients to handle).
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
