package me.iynotuo.judge.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for an email verification code.
 */
public record SendEmailCodeRequest(

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,

        // Optional in the DTO because CAPTCHA can be disabled per deployment; the service returns
        // 2010 when it is enabled and these fields are missing or invalid.
        @Size(max = 64, message = "图形验证码 id 过长")
        String captchaId,

        @Size(max = 16, message = "图形验证码过长")
        String captchaCode
) {
}
