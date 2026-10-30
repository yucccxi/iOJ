package me.iynotuo.judge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 相关配置。
 */
@ConfigurationProperties(prefix = "ioj.jwt")
public class JwtProperties {

    /**
     * HMAC-SHA256 密钥，长度必须 >= 32 字节，生产环境请通过环境变量 IOJ_JWT_SECRET 注入。
     */
    private String secret = "ioj-development-secret-key-please-change-me";

    /**
     * access token 有效期。
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
