package me.iynotuo.judge.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "昵称不能为空")
        @Size(max = 32, message = "昵称长度不能超过 32")
        String nickname
) {
}
