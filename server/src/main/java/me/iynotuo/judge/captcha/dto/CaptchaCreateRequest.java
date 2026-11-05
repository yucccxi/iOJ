package me.iynotuo.judge.captcha.dto;

import jakarta.validation.constraints.NotNull;
import me.iynotuo.judge.common.enums.CaptchaPurpose;

/**
 * Asks for a new image challenge for one action.
 */
public record CaptchaCreateRequest(

        @NotNull(message = "purpose 不能为空")
        CaptchaPurpose purpose
) {
}
