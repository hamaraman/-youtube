package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.ProfileController.ProfileUser;
import com.example.demo.service.ProfileService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProfileController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ProfileControllerMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProfileService profileService;
    @MockitoBean private LoginUserResolver loginUserResolver;

    private SessionUser me() {
        return new SessionUser(10L, "alice", "앨리스", "e@e.com", "Alice TV", "img", "USER");
    }

    private ProfileUser sampleProfile() {
        return new ProfileUser(10L, "alice", "앨리스", "e@e.com", "Alice TV", "img", "banner", "bio");
    }

    @Test
    void getProfile_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(null);

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getProfile_success_returnsProfileResponse() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(profileService.getProfile(10L)).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.channelName").value("Alice TV"));
    }

    @Test
    void updateProfile_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(null);

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "n"))))
                .andExpect(status().isUnauthorized());
        verify(profileService, never()).updateProfile(any(), any());
    }

    @Test
    void updateProfile_success_returnsUpdatedProfile() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(profileService.updateProfile(eq(10L), any()))
                .thenReturn(new SessionUser(10L, "alice", "새닉", "new@e.com", "새채널", "img", "USER"));
        when(profileService.getProfile(10L)).thenReturn(sampleProfile());

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "새닉",
                                "email", "new@e.com",
                                "channelName", "새채널"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user").exists());
    }

    @Test
    void updateProfile_invalidNickname_returns400WithMessage() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(profileService.updateProfile(eq(10L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임을 입력해줘."));

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void sendPasswordChangeCode_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(null);

        mockMvc.perform(post("/api/profile/password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "cur",
                                "newPassword", "newpass12",
                                "confirmPassword", "newpass12"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendPasswordChangeCode_success_returnsOk() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());

        mockMvc.perform(post("/api/profile/password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "cur",
                                "newPassword", "newpass12",
                                "confirmPassword", "newpass12"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void sendPasswordChangeCode_wrongCurrent_returns400() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 틀렸어."))
                .when(profileService).sendPasswordChangeCode(eq(10L), any());

        mockMvc.perform(post("/api/profile/password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrong",
                                "newPassword", "newpass12",
                                "confirmPassword", "newpass12"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void changePassword_success_returnsOk() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());

        mockMvc.perform(put("/api/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("verificationCode", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void changePassword_invalidCode_returns400() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 틀렸거나 만료되었어."))
                .when(profileService).changePassword(eq(10L), any());

        mockMvc.perform(put("/api/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("verificationCode", "bad"))))
                .andExpect(status().isBadRequest());
    }
}
