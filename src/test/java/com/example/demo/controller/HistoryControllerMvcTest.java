package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.VideoController.VideoItem;
import com.example.demo.entity.Video;
import com.example.demo.service.HistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HistoryController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class HistoryControllerMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private HistoryService historyService;
    @MockitoBean private LoginUserResolver loginUserResolver;

    private VideoItem item(Long id) {
        Video v = new Video();
        v.setId(id); v.setOwnerId(5L); v.setTitle("t");
        v.setChannel("ch"); v.setThumbnail("th"); v.setDuration("1:00");
        return VideoItem.from(v, 0L, 0L, false, false);
    }

    @Test
    void markHistory_anonymous_persistsAnonMapInSession() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/videos/1/history").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("조회수 처리됨"));

        assertThat(session.getAttribute("anonViewedVideoTimestamps")).isNotNull();
        verify(historyService).markHistory(eq(1L), eq(null), anyMap());
    }

    @Test
    void markHistory_loggedIn_returnsWatchHistoryMessage() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(post("/api/videos/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("시청 기록 저장됨"));
    }

    @Test
    void markHistory_videoNotFound_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."))
                .when(historyService).markHistory(eq(99L), any(), anyMap());

        mockMvc.perform(post("/api/videos/99/history"))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveProgress_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(put("/api/videos/1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("position", 42.5))))
                .andExpect(status().isUnauthorized());
        verify(historyService, never()).saveProgress(any(), any(), any());
    }

    @Test
    void saveProgress_success_returnsOk() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(put("/api/videos/1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("position", 42.5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(historyService).saveProgress(1L, 42.5, 10L);
    }

    @Test
    void saveProgress_invalidPosition_returns400() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 진행 위치입니다."))
                .when(historyService).saveProgress(eq(1L), any(), eq(10L));

        mockMvc.perform(put("/api/videos/1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("position", -1.0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProgress_anonymous_returnsZero() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);
        when(historyService.getProgress(1L, null)).thenReturn(0.0);

        mockMvc.perform(get("/api/videos/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(0.0));
    }

    @Test
    void getProgress_loggedIn_returnsStoredValue() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(historyService.getProgress(1L, 10L)).thenReturn(88.0);

        mockMvc.perform(get("/api/videos/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(88.0));
    }

    @Test
    void getMyProgress_returnsMap() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(historyService.getMyProgress(10L)).thenReturn(Map.of(1L, 50.0, 2L, 20.0));

        mockMvc.perform(get("/api/my-progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.1").value(50.0))
                .andExpect(jsonPath("$.2").value(20.0));
    }

    @Test
    void getMyHistory_notLoggedIn_returns401WithEmptyBody() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(get("/api/my-history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
        verify(historyService, never()).getMyHistory(any());
    }

    @Test
    void getMyHistory_loggedIn_returnsList() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(historyService.getMyHistory(10L)).thenReturn(List.of(item(1L), item(2L)));

        mockMvc.perform(get("/api/my-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    private static org.assertj.core.api.AbstractAssert<?, ?> assertThat(Object o) {
        return org.assertj.core.api.Assertions.assertThat(o);
    }
}
