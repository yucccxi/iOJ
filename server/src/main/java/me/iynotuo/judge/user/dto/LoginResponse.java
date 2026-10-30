package me.iynotuo.judge.user.dto;

/**
 * Returned on a successful login. The token is sent as Authorization: Bearer <token>.
 */
public record LoginResponse(String token, String tokenType, long expiresIn, UserVO user) {

    public static LoginResponse of(String token, long expiresInSeconds, UserVO user) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, user);
    }
}
