package me.iynotuo.judge.security;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.common.enums.UserStatus;
import me.iynotuo.judge.config.JwtProperties;
import me.iynotuo.judge.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void createsTokenThatCanBeParsed() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofHours(1)));

        String token = provider.createToken(user());

        assertThat(token).isNotBlank();
        assertThat(provider.parseUserId(token)).isEqualTo(42L);
        assertThat(provider.expirationSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofHours(1)));
        String token = provider.createToken(user());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(provider.parseUserId(tampered)).isNull();
    }

    @Test
    void rejectsTokenFromAnotherSecret() {
        JwtTokenProvider issuer = new JwtTokenProvider(properties(Duration.ofHours(1)));
        JwtProperties other = properties(Duration.ofHours(1));
        other.setSecret("another-unit-test-secret-key-32-bytes!");
        JwtTokenProvider verifier = new JwtTokenProvider(other);

        assertThat(verifier.parseUserId(issuer.createToken(user()))).isNull();
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofSeconds(-10)));

        assertThat(provider.parseUserId(provider.createToken(user()))).isNull();
    }

    @Test
    void refusesTooShortSecret() {
        JwtProperties weak = properties(Duration.ofHours(1));
        weak.setSecret("too-short");

        assertThatThrownBy(() -> new JwtTokenProvider(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
