package com.campus.user.service;

import com.campus.user.dto.*;

public interface UserService {

    /**
     * 用户注册
     * @return 新用户ID
     */
    Long register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 刷新 AccessToken
     */
    TokenRefreshResponse refreshToken(TokenRefreshRequest request);

    /**
     * 获取当前登录用户信息
     */
    MeResponse getMe(Long userId);
}
