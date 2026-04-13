package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.config.JwtProperties;
import com.campus.common.constant.RedisKeyConstant;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.common.util.JwtUtil;
import com.campus.user.dto.*;
import com.campus.user.entity.SysUser;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Override
    public Long register(RegisterRequest request) {
        // 1. 用户名唯一性校验
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 构建实体并入库
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(0);   // 默认学生角色
        user.setStatus(1); // 正常状态

        userMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        // 2. 校验密码
        if (!PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }

        // 3. 生成 Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        // 4. 存储 refreshToken 到 Redis（覆盖旧 token，实现单端登录）
        String redisKey = String.format(RedisKeyConstant.USER_REFRESH_TOKEN, user.getId());
        redisTemplate.opsForValue().set(redisKey, refreshToken, jwtProperties.getRefreshExpire(), TimeUnit.MILLISECONDS);

        // 5. 更新最后登录时间（异步不影响响应速度，此处同步简化实现）
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLogin(LocalDateTime.now());
        userMapper.updateById(update);

        long expiresIn = jwtProperties.getAccessExpire() / 1000;
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getAvatarUrl());
        return new LoginResponse(accessToken, refreshToken, expiresIn, userInfo);
    }

    @Override
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(request.getRefreshToken());
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken无效或已过期");
        }

        Long userId = jwtUtil.getUserId(claims);

        // 校验 Redis 中的 refreshToken 是否一致（防止重放/已注销）
        String redisKey = String.format(RedisKeyConstant.USER_REFRESH_TOKEN, userId);
        String storedToken = redisTemplate.opsForValue().get(redisKey);
        if (!request.getRefreshToken().equals(storedToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken已失效，请重新登录");
        }

        String username = jwtUtil.getUsername(claims);
        Integer role = jwtUtil.getRole(claims);
        String newAccessToken = jwtUtil.generateAccessToken(userId, username, role);

        long expiresIn = jwtProperties.getAccessExpire() / 1000;
        return new TokenRefreshResponse(newAccessToken, expiresIn);
    }

    @Override
    public MeResponse getMe(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        MeResponse resp = new MeResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setRole(user.getRole());
        resp.setAvatarUrl(user.getAvatarUrl());
        return resp;
    }
}
