package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.api.club.ClubFeignClient;
import com.campus.api.club.ClubMemberDTO;
import com.campus.common.config.JwtProperties;
import com.campus.common.constant.RedisKeyConstant;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.common.result.Result;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final ClubFeignClient clubFeignClient;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    @Override
    public Long register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(0);
        user.setStatus(1);

        userMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        if (!PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        String redisKey = String.format(RedisKeyConstant.USER_REFRESH_TOKEN, user.getId());
        redisTemplate.opsForValue().set(redisKey, refreshToken, jwtProperties.getRefreshExpire(), TimeUnit.MILLISECONDS);

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

        try {
            Result<List<ClubMemberDTO>> clubResult = clubFeignClient.getUserClubs(userId);
            if (clubResult != null && clubResult.getData() != null) {
                List<MeResponse.ClubInfo> clubs = clubResult.getData().stream()
                        .map(cm -> {
                            MeResponse.ClubInfo info = new MeResponse.ClubInfo();
                            info.setClubId(cm.getClubId());
                            info.setClubName(cm.getClubName());
                            info.setMemberRole(cm.getMemberRole());
                            return info;
                        })
                        .collect(Collectors.toList());
                resp.setClubs(clubs);
            } else {
                resp.setClubs(Collections.emptyList());
            }
        } catch (feign.FeignException e) {
            log.warn("获取用户社团信息失败 (Feign 异常): userId={}, status={}, errorType={}",
                    userId, e.status(), e.getClass().getSimpleName());
            resp.setClubs(Collections.emptyList());
        } catch (Exception e) {
            log.warn("获取用户社团信息失败: userId={}, errorType={}",
                    userId, e.getClass().getSimpleName());
            resp.setClubs(Collections.emptyList());
        }
        return resp;
    }

    @Override
    public void logout(String accessToken, Long userId) {
        blacklistAccessToken(accessToken, userId);

        String refreshKey = String.format(RedisKeyConstant.USER_REFRESH_TOKEN, userId);
        redisTemplate.delete(refreshKey);
        log.info("用户登出成功: userId={}", userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!PASSWORD_ENCODER.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BizException(ErrorCode.PASSWORD_SAME);
        }

        blacklistCurrentUserRefreshToken(userId);

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(PASSWORD_ENCODER.encode(request.getNewPassword()));
        userMapper.updateById(update);
        log.info("密码修改成功: userId={}", userId);
    }

    private void blacklistAccessToken(String accessToken, Long userId) {
        if (accessToken == null) {
            return;
        }
        try {
            Claims claims = jwtUtil.parseToken(accessToken);
            String jti = jwtUtil.getJti(claims);
            long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMillis > 0) {
                String blacklistKey = String.format(RedisKeyConstant.USER_BLACKLIST, jti);
                redisTemplate.opsForValue().set(blacklistKey, "1", remainingMillis, TimeUnit.MILLISECONDS);
                log.info("Token jti 加入黑名单: jti={}, ttlMs={}", jti, remainingMillis);
            }
        } catch (Exception e) {
            log.warn("Token 解析失败（已过期或无效）: userId={}", userId);
        }
    }

    private void blacklistCurrentUserRefreshToken(Long userId) {
        String refreshKey = String.format(RedisKeyConstant.USER_REFRESH_TOKEN, userId);
        String storedRefreshToken = redisTemplate.opsForValue().get(refreshKey);
        if (storedRefreshToken == null) {
            return;
        }

        try {
            Claims refreshClaims = jwtUtil.parseToken(storedRefreshToken);
            String jti = jwtUtil.getJti(refreshClaims);
            long remainingMillis = refreshClaims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMillis > 0) {
                String blacklistKey = String.format(RedisKeyConstant.USER_BLACKLIST, jti);
                redisTemplate.opsForValue().set(blacklistKey, "1", remainingMillis, TimeUnit.MILLISECONDS);
                log.info("当前 refreshToken jti 加入黑名单: userId={}, jti={}", userId, jti);
            }
        } catch (Exception e) {
            log.warn("当前 refreshToken 解析失败（已过期），跳过黑名单: userId={}", userId);
        }
        redisTemplate.delete(refreshKey);
    }
}
