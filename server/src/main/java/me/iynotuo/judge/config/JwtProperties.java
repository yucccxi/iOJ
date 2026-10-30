package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT configuration.
 */
@ConfigurationProperties(prefix = "ioj.jwt")
public class JwtProperties {

    /**
     * HMAC-SHA256 secret. It must be at least 32 bytes and should come from the IOJ_JWT_SECRET environment variable in production.
     */
    private String secret = "ioj-development-secret-key-please-change-me";

    /**
     * Access token lifetime.
     */
    private Duration expiration = Duration.ofDays(7);

    private String issuer = "iOJ";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
