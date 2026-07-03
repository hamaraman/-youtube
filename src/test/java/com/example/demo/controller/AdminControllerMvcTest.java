package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AdminControllerMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AdminService adminService;
    @MockitoBean private AdminChecker adminChecker;
    @MockitoBean private LoginUserResolver loginUserResolver;

    @BeforeEach
    void defaultNonAdmin() {
        when(adminChecker.isAdmin(any(), any())).thenReturn(false);
    }

    private SessionUser adminUser() {
        return new SessionUser(1L, "root", "루트", "r@e.com", "root ch", "img", "ADMIN");
    }

    @Test
    void searchVideos_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/videos/search").param("title", "hi"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
        verify(adminService, never()).searchVideos(anyString());
    }

    @Test
    void searchVideos_admin_returnsList() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(adminService.searchVideos("kw"))
                .thenReturn(List.of(Map.of("id", 1L, "title", "hello")));

        mockMvc.perform(get("/api/admin/videos/search").param("title", "kw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void listVideos_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/videos"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listVideos_admin_returnsList() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(adminService.listVideos()).thenReturn(List.of(Map.of("id", 1L, "title", "t")));

        mockMvc.perform(get("/api/admin/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void deleteVideo_nonAdmin_returns403WithBody() throws Exception {
        mockMvc.perform(delete("/api/admin/videos/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteVideo_admin_success() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);

        mockMvc.perform(delete("/api/admin/videos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(adminService).deleteVideo(1L);
    }

    @Test
    void deleteVideo_notFound_returns404() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."))
                .when(adminService).deleteVideo(99L);

        mockMvc.perform(delete("/api/admin/videos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listBrokenVideos_admin_returnsList() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(adminService.listBrokenVideos())
                .thenReturn(List.of(Map.of("id", 1L, "reason", "영상 소스 없음")));

        mockMvc.perform(get("/api/admin/videos/broken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("영상 소스 없음"));
    }

    @Test
    void bulkDelete_emptyList_returns400() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/admin/videos/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void bulkDelete_success_returnsCount() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(adminService.bulkDeleteVideos(List.of(1L, 2L, 3L))).thenReturn(3);

        mockMvc.perform(post("/api/admin/videos/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(1, 2, 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void listUsers_admin_returnsList() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(adminService.listUsers())
                .thenReturn(List.of(Map.of("id", 1L, "username", "alice", "role", "USER")));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void deleteUser_nonAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/api/admin/users/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_admin_passesOwnIdToService() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(loginUserResolver.getUser(any())).thenReturn(adminUser());

        mockMvc.perform(delete("/api/admin/users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(adminService).deleteUser(5L, 1L);
    }

    @Test
    void deleteUser_selfDelete_returns400WithMessage() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        when(loginUserResolver.getUser(any())).thenReturn(adminUser());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 삭제할 수 없습니다."))
                .when(adminService).deleteUser(eq(1L), eq(1L));

        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void setUserRole_admin_success() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/admin/users/5/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        verify(adminService).setUserRole(5L, "ADMIN");
    }

    @Test
    void setUserRole_invalidRole_returns400() throws Exception {
        when(adminChecker.isAdmin(any(), any())).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 role입니다."))
                .when(adminService).setUserRole(5L, "MODERATOR");

        mockMvc.perform(post("/api/admin/users/5/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "MODERATOR"))))
                .andExpect(status().isBadRequest());
    }
}
