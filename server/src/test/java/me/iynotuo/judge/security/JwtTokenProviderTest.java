package me.iynotuo.judge.security;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.common.enums.UserStatus;
import me.iynotuo.judge.config.JwtProperties;
import me.iynotuo.judge.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}: token round-tripping plus rejection of
 * tampered, wrongly signed and expired tokens and of secrets that are too short.
 */
class JwtTokenProviderTest {

    private User user() {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private JwtProperties properties(Duration expiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-with-32-bytes-minimum");
        properties.setIssuer("iOJ");
        properties.setExpiration(expiration);
        return properties;
    }

    /**
     * Creates a token and verifies that the subject (user id) and the lifetime can be read back.
     */
    @Test
    void createsTokenThatCanBeParsed() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofHours(1)));

        String token = provider.createToken(user());

        assertThat(token).isNotBlank();
        assertThat(provider.parseUserId(token)).isEqualTo(42L);
        assertThat(provider.expirationSeconds()).isEqualTo(3600);
    }

    /**
     * Rejects a token whose signature no longer matches the payload.
     */
    @Test
    void rejectsTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofHours(1)));
        String token = provider.createToken(user());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(provider.parseUserId(tampered)).isNull();
    }

    /**
     * Rejects a valid token that was signed with a different secret.
     */
    @Test
    void rejectsTokenFromAnotherSecret() {
        JwtTokenProvider issuer = new JwtTokenProvider(properties(Duration.ofHours(1)));
        JwtProperties other = properties(Duration.ofHours(1));
        other.setSecret("another-unit-test-secret-key-32-bytes!");
        JwtTokenProvider verifier = new JwtTokenProvider(other);

        assertThat(verifier.parseUserId(issuer.createToken(user()))).isNull();
    }

    /**
     * Rejects a token whose expiration time is already in the past.
     */
    @Test
    void rejectsExpiredToken() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofSeconds(-10)));

        assertThat(provider.parseUserId(provider.createToken(user()))).isNull();
    }

    /**
     * Refuses to start when the configured secret is shorter than 32 bytes.
     */
    @Test
    void refusesTooShortSecret() {
        JwtProperties weak = properties(Duration.ofHours(1));
        weak.setSecret("too-short");

        assertThatThrownBy(() -> new JwtTokenProvider(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
