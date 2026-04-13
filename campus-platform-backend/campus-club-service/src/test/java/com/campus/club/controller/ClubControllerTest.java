package com.campus.club.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.service.ClubService;
import com.campus.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClubControllerTest {

    private MockMvc mockMvc;
    private final ClubService clubService = mock(ClubService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ClubController controller = new ClubController(clubService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createClub_success() throws Exception {
        when(clubService.createClub(any(), anyLong())).thenReturn(1L);

        mockMvc.perform(post("/api/v1/clubs")
                        .header("X-User-Id", "1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Chess Club"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void joinClub_success() throws Exception {
        doNothing().when(clubService).joinClub(anyLong(), anyLong());

        mockMvc.perform(post("/api/v1/clubs/1/join")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void quitClub_success() throws Exception {
        doNothing().when(clubService).quitClub(anyLong(), anyLong());

        mockMvc.perform(post("/api/v1/clubs/1/quit")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void kickMember_success() throws Exception {
        doNothing().when(clubService).kickMember(anyLong(), anyLong(), anyLong());

        mockMvc.perform(delete("/api/v1/clubs/1/members/100")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void approveMember_success() throws Exception {
        doNothing().when(clubService).approveMember(anyLong(), anyLong(), anyLong(), anyBoolean());

        mockMvc.perform(post("/api/v1/clubs/1/members/100/approve")
                        .header("X-User-Id", "1")
                        .param("approved", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserClubs_success() throws Exception {
        when(clubService.getUserClubs(anyLong())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/clubs/internal/members")
                        .param("userId", "1"))
                .andExpect(status().isOk());
    }
}
