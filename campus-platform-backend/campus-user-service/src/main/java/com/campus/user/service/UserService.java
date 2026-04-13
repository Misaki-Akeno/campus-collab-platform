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
     * 获取当前登录用户信息（含 clubs 字段，通过 ClubFeignClient 跨服务查询）
     */
    MeResponse getMe(Long userId);

    /**
     * 用户登出：将 accessToken 的 jti 写入黑名单（TTL = Token 剩余有效期），
     * 同时删除 Redis 中的 refreshToken，使双 Token 同步失效。
     *
     * @param accessToken 原始 accessToken 字符串（可为 null，此时仅清除 refreshToken）
     * @param userId      当前用户 ID（来自 Gateway 注入的 X-User-Id）
     */
    void logout(String accessToken, Long userId);

    /**
     * 修改密码：校验旧密码，更新新密码，并将当前 refreshToken 加入黑名单强制重新登录。
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
