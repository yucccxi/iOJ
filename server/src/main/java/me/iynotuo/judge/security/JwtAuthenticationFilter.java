package me.iynotuo.judge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.iynotuo.judge.common.enums.UserStatus;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Parses Authorization: Bearer <token> and, once validated, puts a LoginUser into the SecurityContext.
 * Reads the user row on every request so bans and role changes take effect immediately, which is cheap at club scale.
 *
 * <p>This class deliberately has no @Component annotation; me.iynotuo.judge.config.SecurityConfig
 * declares it as a bean so it only runs in the security filter chain and is not registered twice by the servlet container.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserMapper userMapper) {
        this.tokenProvider = tokenProvider;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = tokenProvider.parseUserId(header.substring(PREFIX.length()).trim());
            if (userId != null) {
                User user = userMapper.findById(userId);
                if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                    LoginUser principal = new LoginUser(user.getId(), user.getUsername(),
                            user.getNickname(), user.getRole());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
