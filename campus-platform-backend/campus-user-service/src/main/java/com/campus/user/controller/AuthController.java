package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.user.dto.*;
import com.campus.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** POST /api/v1/register — 用户注册 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return Result.ok("注册成功", Map.of("userId", String.valueOf(userId), "username", request.getUsername()));
    }

    /** POST /api/v1/login — 用户登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.ok("登录成功", response);
    }

    /** POST /api/v1/token/refresh — 刷新 AccessToken */
    @PostMapping("/token/refresh")
    public Result<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return Result.ok(userService.refreshToken(request));
    }

    /** GET /api/v1/me — 获取当前登录用户信息（需通过 Gateway 鉴权，X-User-Id 由 Gateway 注入） */
    @GetMapping("/me")
    public Result<MeResponse> getMe(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(userService.getMe(userId));
    }

    /**
     * POST /api/v1/logout — 用户登出。
     * accessToken 通过 Request Body 传递，原因：Gateway 会移除 Authorization Header，
     * 无法在业务服务侧直接读取，改用 Body 传递以便提取 jti 写入黑名单。
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody LogoutRequest request) {
        userService.logout(request.getAccessToken(), userId);
        return Result.ok("已成功登出", null);
    }

    /** PUT /api/v1/me/password — 修改密码（改密后需重新登录） */
    @PutMapping("/me/password")
    public Result<Void> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return Result.ok("密码修改成功，请重新登录", null);
    }
}
