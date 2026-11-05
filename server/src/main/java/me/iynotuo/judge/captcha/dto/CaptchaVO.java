package me.iynotuo.judge.captcha.dto;

/**
 * A newly issued challenge. {@code image} is a complete {@code data:image/png;base64,...} URL.
 */
public record CaptchaVO(String captchaId, String image, int expiresInSeconds) {
}
