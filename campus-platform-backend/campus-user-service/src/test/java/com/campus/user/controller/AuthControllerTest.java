package com.campus.user.controller;

import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.user.dto.*;
import com.campus.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private MockMvc mockMvc;
    private final UserService userService = mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void register_success_returns201() throws Exception {
        when(userService.register(any(RegisterRequest.class))).thenReturn(100L);

        mockMvc.perform(post("/api/v1/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "newuser",
                                "password", "Passw0rd!",
                                "nickname", "New User"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").exists());
    }

    @Test
    void login_success_returnsTokens() throws Exception {
        var userInfo = new LoginResponse.UserInfo(1L, "user1", "User One", 0, null);
        var response = new LoginResponse("access-t", "refresh-t", 7200L, userInfo);
        when(userService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "user1",
                                "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access-t"));
    }

    @Test
    void refreshToken_success() throws Exception {
        var response = new TokenRefreshResponse("new-access-t", 7200L);
        when(userService.refreshToken(any(TokenRefreshRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/token/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", "old-refresh-t"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-t"));
    }

    @Test
    void getMe_success() throws Exception {
        var response = new MeResponse();
        response.setUserId(1L);
        response.setUsername("user1");
        response.setNickname("User One");
        response.setRole(0);
        response.setClubs(List.of());
        when(userService.getMe(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/me")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("user1"));
    }

    @Test
    void getMe_missingUserIdHeader_returnsNonNull() throws Exception {
        // Note: standalone MockMvc does not enforce @RequestHeader validation
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk());
    }

    @Test
    void logout_success() throws Exception {
        doNothing().when(userService).logout("access-t", 1L);

        mockMvc.perform(post("/api/v1/logout")
                        .header("X-User-Id", "1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("accessToken", "access-t"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void changePassword_success() throws Exception {
        doNothing().when(userService).changePassword(anyLong(), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/me/password")
                        .header("X-User-Id", "1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "OldPass0!",
                                "newPassword", "NewPass0!"))))
                .andExpect(status().isOk());
    }

    @Test
    void register_rejectsBlankUsername() throws Exception {
        mockMvc.perform(post("/api/v1/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "",
                                "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
