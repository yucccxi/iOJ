package me.iynotuo.judge.user.dto;

/**
 * 登录成功返回。token 放在 Authorization: Bearer 中携带。
 */
public record LoginResponse(String token, String tokenType, long expiresIn, UserVO user) {

    public static LoginResponse of(String token, long expiresInSeconds, UserVO user) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, user);
    }
}
