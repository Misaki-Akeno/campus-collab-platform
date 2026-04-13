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
}
