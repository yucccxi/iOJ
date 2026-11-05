package me.iynotuo.judge.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import me.iynotuo.judge.config.JwtProperties;
import me.iynotuo.judge.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies JWTs (HS256). The payload carries id, username, role and the user's token
 * version; authorities always come from the database.
 *
 * <p>The token version is deliberately part of the signed payload. The authentication filter
 * compares it with {@code users.token_version}, so changing the password or logging out all devices
 * invalidates every JWT issued before that event.</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "ioj.jwt.secret 至少需要 32 字节（256 bit），当前为 " + secret.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getExpiration())))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates a token.
     *
     * @return the user id and token version, or null when the token is invalid or expired
     */
    public TokenClaims parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new TokenClaims(Long.valueOf(claims.getSubject()), tokenVersion(claims));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Compatibility helper for callers that only need the user id.
     */
    public Long parseUserId(String token) {
        TokenClaims claims = parseToken(token);
        return claims == null ? null : claims.userId();
    }

    public long expirationSeconds() {
        return properties.getExpiration().toSeconds();
    }

    /**
     * Reads the expected token version from the already-verified claims. A missing version means
     * the token was issued before token versioning existed and must be rejected.
     */
    private Integer tokenVersion(Claims claims) {
        Object value = claims.get(CLAIM_TOKEN_VERSION);
        if (!(value instanceof Number number)) {
            return null;
        }
        return number.intValue();
    }

    /**
     * Minimum claims needed by {@link JwtAuthenticationFilter}.
     */
    public record TokenClaims(Long userId, Integer tokenVersion) {
    }
}
