package me.iynotuo.judge.config;

import me.iynotuo.judge.security.JwtAuthenticationFilter;
import me.iynotuo.judge.security.JwtTokenProvider;
import me.iynotuo.judge.security.RestAccessDeniedHandler;
import me.iynotuo.judge.security.RestAuthenticationEntryPoint;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT authentication. Public endpoints (problem browsing, problem detail, public submission list) allow anonymous access;
 * all other endpoints require authentication, and /api/v1/admin/** requires the ADMIN role.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Declared as a bean so the filter only runs inside the security filter chain and is not registered again by the servlet container.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserMapper userMapper) {
        return new JwtAuthenticationFilter(tokenProvider, userMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // The checker source decides how answers are judged, so it is visible to the author and administrators only.
                        .requestMatchers("/api/v1/problems/*/checker").authenticated()
                        // Test data is visible to the author and administrators only, so this rule must come before the public GET rules below.
                        .requestMatchers("/api/v1/problems/*/testcases/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/problems/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/problems/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/meta/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/submissions", "/api/v1/submissions/*").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${ioj.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
