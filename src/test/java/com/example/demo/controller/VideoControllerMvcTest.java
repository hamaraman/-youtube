package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.VideoController.VideoItem;
import com.example.demo.controller.VideoController.VideoUpdateRequest;
import com.example.demo.entity.Video;
import com.example.demo.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = VideoController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class VideoControllerMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private VideoService videoService;
    @MockitoBean private LoginUserResolver loginUserResolver;
    @MockitoBean private AdminChecker adminChecker;

    private VideoItem sampleItem(Long id, String title) {
        Video v = new Video();
        v.setId(id); v.setOwnerId(5L); v.setTitle(title);
        v.setChannel("ch"); v.setThumbnail("thumb.png"); v.setDuration("1:00");
        v.setVisibility("공개");
        return VideoItem.from(v, 0L, 0L, false, false);
    }

    @Test
    void getVideos_forwardsAdminFlagAndKeyword() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(videoService.getVideos(eq("kw"), eq(10L), eq(true)))
                .thenReturn(List.of(sampleItem(1L, "t")));

        mockMvc.perform(get("/api/videos").param("keyword", "kw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("t"));
    }

    @Test
    void getRelatedVideos_returnsRecommendedAndChannelLists() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);
        when(videoService.getRelatedVideos(eq(1L), any(), eq(12)))
                .thenReturn(Map.of(
                        "recommended", List.of(sampleItem(2L, "rec")),
                        "channel", List.of(sampleItem(3L, "ch"))));

        mockMvc.perform(get("/api/videos/1/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommended[0].id").value(2))
                .andExpect(jsonPath("$.channel[0].id").value(3));
    }

    @Test
    void pathVariableTypeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/api/videos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getRelatedVideos_missingVideo_returns404() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);
        when(videoService.getRelatedVideos(eq(99L), any(), eq(12)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/videos/99/related"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVideos_anonymous_treatedAsNonAdmin() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);
        when(adminChecker.isAdmin(any(), any())).thenReturn(false);
        when(videoService.getVideos(eq(null), eq(null), eq(false)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getChannelProfile_notFound_returns404() throws Exception {
        when(videoService.getChannelProfile(eq(5L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "채널을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/users/5/channel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChannelProfile_returnsProfile() throws Exception {
        when(videoService.getChannelProfile(eq(5L), any()))
                .thenReturn(Map.of(
                        "id", 5L, "channelName", "Alice TV", "subscriberCount", 100L,
                        "videoCount", 3L, "subscribed", true, "isMe", false));

        mockMvc.perform(get("/api/users/5/channel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channelName").value("Alice TV"))
                .andExpect(jsonPath("$.subscriberCount").value(100));
    }

    @Test
    void getSubscriptionFeed_returnsPayload() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(videoService.getSubscriptionFeed(eq(10L), eq(0), eq(12)))
                .thenReturn(Map.of("videos", List.of(), "hasMore", false, "page", 0, "totalElements", 0L));

        mockMvc.perform(get("/api/videos/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void getCategories_returnsList() throws Exception {
        when(videoService.getCategories()).thenReturn(List.of("게임", "음악"));

        mockMvc.perform(get("/api/videos/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("게임"))
                .andExpect(jsonPath("$[1]").value("음악"));
    }

    @Test
    void getFeed_forwardsAllQueryParams() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(videoService.getFeed(eq(2), eq(20), eq("kw"), eq("게임"), eq(5L),
                eq("popular"), eq(null), eq(10L)))
                .thenReturn(Map.of("videos", List.of(), "hasMore", true, "page", 2, "totalElements", 100L));

        mockMvc.perform(get("/api/videos/feed")
                        .param("page", "2").param("size", "20")
                        .param("keyword", "kw").param("category", "게임")
                        .param("ownerId", "5").param("sort", "popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.totalElements").value(100));
    }

    @Test
    void getFeed_usesDefaultsWhenParamsMissing() throws Exception {
        when(videoService.getFeed(eq(0), eq(12), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("videos", List.of(), "hasMore", false, "page", 0, "totalElements", 0L));

        mockMvc.perform(get("/api/videos/feed"))
                .andExpect(status().isOk());
        verify(videoService).getFeed(eq(0), eq(12), eq(null), eq(null), eq(null), eq(null), eq(null), any());
    }

    @Test
    void getVideoById_privateForbidden_returns403WithMessage() throws Exception {
        when(videoService.getVideoById(eq(1L), any(), anyBoolean()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "비공개 영상입니다."));

        mockMvc.perform(get("/api/videos/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("비공개 영상입니다."));
    }

    @Test
    void getVideoById_notFound_returns404WithoutBody() throws Exception {
        when(videoService.getVideoById(eq(1L), any(), anyBoolean()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/videos/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVideoById_success_returnsItem() throws Exception {
        when(videoService.getVideoById(eq(1L), any(), anyBoolean()))
                .thenReturn(sampleItem(1L, "hello"));

        mockMvc.perform(get("/api/videos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("hello"));
    }

    @Test
    void getStudioVideos_notLoggedIn_returns401() throws Exception {
        when(videoService.getStudioVideos(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        mockMvc.perform(get("/api/studio/videos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getStudioVideos_success_returnsList() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(videoService.getStudioVideos(5L)).thenReturn(List.of(sampleItem(1L, "t")));

        mockMvc.perform(get("/api/studio/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getViewTrend_defaultsTo28Days() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(videoService.getViewTrend(eq(5L), eq(28)))
                .thenReturn(List.of(Map.of("date", "2026-07-01", "count", 3L)));

        mockMvc.perform(get("/api/studio/view-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-07-01"));
        verify(videoService).getViewTrend(5L, 28);
    }

    @Test
    void getMyVideos_returnsList() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(videoService.getMyVideos(5L)).thenReturn(List.of(sampleItem(1L, "t")));

        mockMvc.perform(get("/api/my-videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void updateVideo_notOwner_returns403WithMessage() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(999L);
        when(videoService.updateVideo(eq(1L), eq(999L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 영상만 수정할 수 있습니다."));

        VideoUpdateRequest r = new VideoUpdateRequest();
        r.setTitle("t"); r.setDescription("d"); r.setChannel("c"); r.setDuration("1:00");

        mockMvc.perform(put("/api/videos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void updateVideo_success_returnsUpdatedItem() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(videoService.updateVideo(eq(1L), eq(5L), any()))
                .thenReturn(sampleItem(1L, "updated"));

        VideoUpdateRequest r = new VideoUpdateRequest();
        r.setTitle("updated"); r.setDescription("d"); r.setChannel("c"); r.setDuration("1:00");

        mockMvc.perform(put("/api/videos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("updated"));
    }

    @Test
    void deleteVideo_notLoggedIn_returns401() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."))
                .when(videoService).deleteVideo(eq(1L), any());

        mockMvc.perform(delete("/api/videos/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteVideo_success_returnsMessage() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);

        mockMvc.perform(delete("/api/videos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(videoService).deleteVideo(1L, 5L);
    }

    @Test
    void replaceThumbnail_success_returnsUrl() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(videoService.replaceThumbnail(eq(1L), eq(5L), any()))
                .thenReturn("/uploads/thumbnails/abc.png");

        MockMultipartFile file = new MockMultipartFile(
                "thumbnailFile", "abc.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/videos/1/thumbnail").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.thumbnail").value("/uploads/thumbnails/abc.png"));
    }

    @Test
    void replaceThumbnail_invalidFormat_returns400WithMessage() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(videoService.replaceThumbnail(eq(1L), eq(5L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "허용되지 않는 썸네일 형식입니다."));

        MockMultipartFile file = new MockMultipartFile(
                "thumbnailFile", "abc.txt", "text/plain", new byte[]{1});

        mockMvc.perform(multipart("/api/videos/1/thumbnail").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getMyLikedVideos_forwardsAdminFlag() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(videoService.getMyLikedVideos(5L, true)).thenReturn(List.of(sampleItem(1L, "t")));

        mockMvc.perform(get("/api/my-liked-videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
        verify(videoService).getMyLikedVideos(5L, true);
    }

    @Test
    void getMySavedVideos_nonAdmin_returnsList() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(5L);
        when(adminChecker.isAdmin(any(), any())).thenReturn(false);
        when(videoService.getMySavedVideos(5L, false)).thenReturn(List.of());

        mockMvc.perform(get("/api/my-saved-videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
