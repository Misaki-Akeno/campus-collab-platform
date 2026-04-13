package com.campus.user.service.impl;

import com.campus.api.club.ClubFeignClient;
import com.campus.api.club.ClubMemberDTO;
import com.campus.common.config.JwtProperties;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.common.result.Result;
import com.campus.common.util.JwtUtil;
import com.campus.user.dto.*;
import com.campus.user.entity.SysUser;
import com.campus.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * 测试策略: BCryptPasswordEncoder 使用真实实例（轻量且确定性），仅 mock 外部依赖（Mapper/Redis/Feign/JWT）。
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ClubFeignClient clubFeignClient;

    @InjectMocks
    private UserServiceImpl userService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private long nextId = 1000L;

    @BeforeEach
    void setUp() {
        when(jwtProperties.getAccessExpire()).thenReturn(2 * 60 * 60 * 1000L);
        when(jwtProperties.getRefreshExpire()).thenReturn(7L * 24 * 60 * 60 * 1000);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser u = invocation.getArgument(0);
            u.setId(nextId++);
            return 1;
        });
    }

    // ========== register ==========

    @Test
    void register_success() {
        var req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("Passw0rd!");
        req.setNickname("New User");
        req.setEmail("new@test.com");

        when(userMapper.selectCount(any())).thenReturn(0L);
        when(jwtProperties.getRefreshExpire()).thenReturn(7L * 24 * 60 * 60 * 1000);

        Long userId = userService.register(req);

        assertNotNull(userId);
    }

    @Test
    void register_duplicateUsername_throwsException() {
        var req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("Passw0rd!");

        when(userMapper.selectCount(any())).thenReturn(1L);

        var ex = assertThrows(BizException.class, () -> userService.register(req));
        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void register_nullNickname_defaultsToUsername() {
        var req = new RegisterRequest();
        req.setUsername("autonick");
        req.setPassword("Passw0rd!");
        req.setNickname(null);

        when(userMapper.selectCount(any())).thenReturn(0L);

        userService.register(req);
    }

    // ========== login ==========

    @Test
    void login_success() {
        var req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("Passw0rd!");

        SysUser user = buildSysUser(1L, "user1", "Passw0rd!", "User One", 0, 1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtil.generateAccessToken(1L, "user1", 0)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L, "user1", 0)).thenReturn("refresh-token");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        var resp = userService.login(req);

        assertEquals("access-token", resp.getAccessToken());
        assertEquals("refresh-token", resp.getRefreshToken());
        assertNotNull(resp.getUserInfo());
        assertEquals("User One", resp.getUserInfo().getNickname());
        verify(redisTemplate).opsForValue();
        verify(valueOperations).set(eq("user:refresh:1"), eq("refresh-token"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void login_userNotFound_throwsException() {
        var req = new LoginRequest();
        req.setUsername("nouser");
        req.setPassword("Passw0rd!");

        when(userMapper.selectOne(any())).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> userService.login(req));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void login_userDisabled_throwsException() {
        var req = new LoginRequest();
        req.setUsername("disabled");
        req.setPassword("Passw0rd!");

        SysUser user = buildSysUser(1L, "disabled", "Passw0rd!", "Disabled", 0, 0);
        when(userMapper.selectOne(any())).thenReturn(user);

        var ex = assertThrows(BizException.class, () -> userService.login(req));
        assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void login_wrongPassword_throwsException() {
        var req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("WrongPass1!");

        SysUser user = buildSysUser(1L, "user1", "CorrectPass0!", "User One", 0, 1);
        when(userMapper.selectOne(any())).thenReturn(user);

        var ex = assertThrows(BizException.class, () -> userService.login(req));
        assertEquals(ErrorCode.PASSWORD_ERROR.getCode(), ex.getCode());
    }

    // ========== refreshToken ==========

    @Test
    void refreshToken_success() {
        var req = new TokenRefreshRequest();
        req.setRefreshToken("valid-refresh-token");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");

        when(jwtUtil.parseToken("valid-refresh-token")).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(1L);
        when(jwtUtil.getUsername(claims)).thenReturn("user1");
        when(jwtUtil.getRole(claims)).thenReturn(0);
        when(jwtUtil.generateAccessToken(1L, "user1", 0)).thenReturn("new-access-token");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:refresh:1")).thenReturn("valid-refresh-token");

        var resp = userService.refreshToken(req);

        assertEquals("new-access-token", resp.getAccessToken());
    }

    @Test
    void refreshToken_invalidToken_throwsException() {
        var req = new TokenRefreshRequest();
        req.setRefreshToken("expired-token");

        when(jwtUtil.parseToken("expired-token")).thenThrow(new RuntimeException("Token expired"));

        var ex = assertThrows(BizException.class, () -> userService.refreshToken(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void refreshToken_replayAttack_throwsException() {
        var req = new TokenRefreshRequest();
        req.setRefreshToken("old-refresh-token");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");

        when(jwtUtil.parseToken("old-refresh-token")).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:refresh:1")).thenReturn("newer-refresh-token");

        var ex = assertThrows(BizException.class, () -> userService.refreshToken(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    // ========== getMe ==========

    @Test
    void getMe_success_withClubs() {
        SysUser user = buildSysUser(1L, "user1", "Passw0rd!", "User One", 0, 1);
        when(userMapper.selectById(any())).thenReturn(user);

        var clubDto = new ClubMemberDTO();
        clubDto.setClubId(10L);
        clubDto.setClubName("Chess Club");
        clubDto.setMemberRole(0);
        when(clubFeignClient.getUserClubs(1L)).thenReturn(Result.ok(List.of(clubDto)));

        var resp = userService.getMe(1L);

        assertEquals("User One", resp.getNickname());
        assertEquals(1, resp.getClubs().size());
        assertEquals("Chess Club", resp.getClubs().get(0).getClubName());
    }

    @Test
    void getMe_feignFailure_returnsEmptyClubs() {
        SysUser user = buildSysUser(1L, "user1", "Passw0rd!", "User One", 0, 1);
        when(userMapper.selectById(any())).thenReturn(user);
        when(clubFeignClient.getUserClubs(1L)).thenThrow(new RuntimeException("Feign error"));

        var resp = userService.getMe(1L);

        assertNotNull(resp);
        assertNotNull(resp.getClubs());
        assertTrue(resp.getClubs().isEmpty());
    }

    @Test
    void getMe_userNotFound_throwsException() {
        when(userMapper.selectById(99L)).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> userService.getMe(99L));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== logout ==========

    @Test
    void logout_success() {
        String accessToken = "valid-access-token";
        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
        when(claims.get("id", String.class)).thenReturn("jti-123");
        when(jwtUtil.parseToken(accessToken)).thenReturn(claims);
        when(jwtUtil.getJti(claims)).thenReturn("jti-123");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(redisTemplate.delete("user:refresh:1")).thenReturn(true);

        userService.logout(accessToken, 1L);

        verify(redisTemplate).delete("user:refresh:1");
    }

    // ========== changePassword ==========

    @Test
    void changePassword_success() {
        var req = new ChangePasswordRequest();
        req.setOldPassword("OldPass0!");
        req.setNewPassword("NewPass0!");

        SysUser user = buildSysUser(1L, "user1", "OldPass0!", "User One", 0, 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:refresh:1")).thenReturn("old-refresh-token");
        when(jwtProperties.getRefreshExpire()).thenReturn(7L * 24 * 60 * 60 * 1000);

        userService.changePassword(1L, req);
        verify(redisTemplate).delete("user:refresh:1");
    }

    @Test
    void changePassword_wrongOldPassword_throwsException() {
        var req = new ChangePasswordRequest();
        req.setOldPassword("WrongOld0!");
        req.setNewPassword("NewPass0!");

        SysUser user = buildSysUser(1L, "user1", "CorrectOld0!", "User One", 0, 1);
        when(userMapper.selectById(1L)).thenReturn(user);

        var ex = assertThrows(BizException.class, () -> userService.changePassword(1L, req));
        assertEquals(ErrorCode.PASSWORD_ERROR.getCode(), ex.getCode());
    }

    @Test
    void changePassword_samePassword_throwsException() {
        var req = new ChangePasswordRequest();
        req.setOldPassword("SamePass0!");
        req.setNewPassword("SamePass0!");

        SysUser user = buildSysUser(1L, "user1", "SamePass0!", "User One", 0, 1);
        when(userMapper.selectById(1L)).thenReturn(user);

        var ex = assertThrows(BizException.class, () -> userService.changePassword(1L, req));
        assertEquals(ErrorCode.PASSWORD_SAME.getCode(), ex.getCode());
    }

    @Test
    void changePassword_userNotFound_throwsException() {
        var req = new ChangePasswordRequest();
        req.setOldPassword("OldPass0!");
        req.setNewPassword("NewPass0!");

        when(userMapper.selectById(99L)).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> userService.changePassword(99L, req));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== helper ==========

    private SysUser buildSysUser(Long id, String username, String rawPassword, String nickname, int role, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(encoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
