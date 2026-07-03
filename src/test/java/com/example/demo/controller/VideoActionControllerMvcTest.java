package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.VideoActionController.LikeResponse;
import com.example.demo.controller.VideoActionController.SaveResponse;
import com.example.demo.service.VideoActionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = VideoActionController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class VideoActionControllerMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private VideoActionService videoActionService;
    @MockitoBean private LoginUserResolver loginUserResolver;

    private SessionUser me() {
        return new SessionUser(10L, "user", "닉", "e@e.com", "채널", "img", "USER");
    }

    @Test
    void toggleLike_success_returnsLikeResponse() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(videoActionService.toggleLike(eq(1L), any(SessionUser.class)))
                .thenReturn(new LikeResponse(true, true, 5L));

        mockMvc.perform(post("/api/videos/1/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(5));
    }

    @Test
    void toggleLike_notLoggedIn_returns401WithMessage() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(null);
        when(videoActionService.toggleLike(eq(1L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        mockMvc.perform(post("/api/videos/1/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void toggleLike_videoNotFound_returnsNotFoundStatus() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(videoActionService.toggleLike(eq(1L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        mockMvc.perform(post("/api/videos/1/like"))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleSave_success_returnsSaveResponse() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(videoActionService.toggleSave(eq(1L), eq(10L)))
                .thenReturn(new SaveResponse(true, true));

        mockMvc.perform(post("/api/videos/1/save"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.saved").value(true));
    }

    @Test
    void toggleSave_notLoggedIn_returns401WithMessage() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);
        when(videoActionService.toggleSave(eq(1L), eq(null)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        mockMvc.perform(post("/api/videos/1/save"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }
}
