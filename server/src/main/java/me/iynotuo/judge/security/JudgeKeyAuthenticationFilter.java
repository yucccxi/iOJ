package me.iynotuo.judge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.iynotuo.judge.judge.entity.JudgeKey;
import me.iynotuo.judge.judge.service.JudgeKeyService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates remote judge agents on {@code /internal/judge/**} with an API key.
 *
 * <p>This filter deliberately does not write its own error response: when the key is missing or
 * invalid it leaves the request unauthenticated, and the standard entry point returns the unified
 * 401 JSON body.</p>
 */
public class JudgeKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String INTERNAL_PREFIX = "/internal/judge/";

    private final JudgeKeyService judgeKeyService;

    public JudgeKeyAuthenticationFilter(JudgeKeyService judgeKeyService) {
        this.judgeKeyService = judgeKeyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HEADER);
            if (header != null && header.startsWith(PREFIX)) {
                JudgeKey key = judgeKeyService.authenticate(header.substring(PREFIX.length()).trim());
                if (key != null) {
                    JudgePrincipal principal = new JudgePrincipal(key.getId(), key.getName());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_JUDGE")));
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
