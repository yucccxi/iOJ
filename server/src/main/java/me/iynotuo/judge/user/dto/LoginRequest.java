package me.iynotuo.judge.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "用户名或邮箱不能为空")
        @Size(max = 128, message = "账号长度不能超过 128")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码长度不能超过 64")
        String password,

        // Optional in the DTO because CAPTCHA can be disabled per deployment; the service returns
        // 2010 when it is enabled and these fields are missing or invalid.
        @Size(max = 64, message = "图形验证码 id 过长")
        String captchaId,

        @Size(max = 16, message = "图形验证码过长")
        String captchaCode
) {
}
