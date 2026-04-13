package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登出请求：通过 Request Body 传递 accessToken。
 * Gateway 会移除 Authorization Header，故改用 Body 传递以便提取 jti 写入黑名单。
 */
@Data
public class LogoutRequest {

    @NotBlank(message = "accessToken 不能为空")
    private String accessToken;
}
