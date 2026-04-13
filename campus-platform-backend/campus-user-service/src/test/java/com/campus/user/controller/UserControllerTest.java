package com.campus.user.controller;

import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.user.entity.SysUser;
import com.campus.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {

    private MockMvc mockMvc;
    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getUserBasic_success() throws Exception {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("user1");
        user.setNickname("User One");
        user.setRole(0);
        user.setAvatarUrl("https://example.com/avatar.png");
        when(userMapper.selectById(any())).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/1/basic"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserBasic_userNotFound_returnsFail() throws Exception {
        when(userMapper.selectById(any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/users/99/basic"))
                .andExpect(status().isOk());
    }
}
