package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.service.PlaylistService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PlaylistController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PlaylistControllerMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PlaylistService playlistService;
    @MockitoBean private LoginUserResolver loginUserResolver;

    @Test
    void getUserPlaylists_returnsList() throws Exception {
        when(playlistService.getUserPlaylists(5L))
                .thenReturn(List.of(Map.of("id", 1L, "name", "즐겨찾기", "videoCount", 3L, "thumbnail", "")));

        mockMvc.perform(get("/api/playlists/user/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("즐겨찾기"));
    }

    @Test
    void getMyPlaylists_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(get("/api/playlists/me"))
                .andExpect(status().isUnauthorized());
        verify(playlistService, never()).getMyPlaylists(any());
    }

    @Test
    void getMyPlaylists_loggedIn_returnsList() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(playlistService.getMyPlaylists(10L))
                .thenReturn(List.of(Map.of("id", 1L, "name", "내 목록", "videoCount", 0L, "thumbnail", "")));

        mockMvc.perform(get("/api/playlists/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("내 목록"));
    }

    @Test
    void createPlaylist_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(post("/api/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "abc"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPlaylist_valid_returnsCreatedInfo() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(playlistService.createPlaylist("새 목록", 10L))
                .thenReturn(Map.of("success", true, "id", 42L, "name", "새 목록"));

        mockMvc.perform(post("/api/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "새 목록"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("새 목록"));
    }

    @Test
    void createPlaylist_blankName_returns400WithMessage() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(playlistService.createPlaylist(eq(""), eq(10L)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "재생목록 이름을 입력해줘."));

        mockMvc.perform(post("/api/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deletePlaylist_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(delete("/api/playlists/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletePlaylist_notOwner_returns403() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다."))
                .when(playlistService).deletePlaylist(1L, 10L);

        mockMvc.perform(delete("/api/playlists/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePlaylist_success_returnsOk() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(delete("/api/playlists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(playlistService).deletePlaylist(1L, 10L);
    }

    @Test
    void addVideo_duplicate_returnsOkWithSuccessFalse() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(playlistService.addVideo(1L, 5L, 10L))
                .thenReturn(Map.of("success", false, "message", "이미 추가된 영상이야."));

        mockMvc.perform(post("/api/playlists/1/videos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("videoId", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addVideo_missingVideo_returns400WithMessage() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(playlistService.addVideo(eq(1L), eq(99L), eq(10L)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "영상을 찾을 수 없어."));

        mockMvc.perform(post("/api/playlists/1/videos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("videoId", 99))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void removeVideo_success_returnsOk() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(delete("/api/playlists/1/videos/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(playlistService).removeVideo(1L, 5L, 10L);
    }

    @Test
    void renamePlaylist_notOwner_returns403WithoutBody() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다."))
                .when(playlistService).renamePlaylist(1L, "새 이름", 10L);

        mockMvc.perform(put("/api/playlists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "새 이름"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void renamePlaylist_success_returnsOk() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(put("/api/playlists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "새 이름"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
