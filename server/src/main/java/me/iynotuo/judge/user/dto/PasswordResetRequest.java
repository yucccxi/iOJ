package me.iynotuo.judge.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Resets a forgotten password with a code mailed to the account address.
 */
public record PasswordResetRequest(

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,

        @NotBlank(message = "邮箱验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码需为 6 位数字")
        String emailCode,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "新密码长度需为 8-64 位")
        String newPassword
) {
}
