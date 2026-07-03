package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.AuthController.LoginResponse;
import com.example.demo.controller.AuthController.MeResponse;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.AuthController.TokenRefreshResponse;
import com.example.demo.service.AuthService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;

    private SessionUser sampleUser() {
        return new SessionUser(10L, "alice", "앨리스", "alice@e.com", "Alice TV", "img", "USER");
    }

    @Test
    void signup_valid_returnsOk() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice42",
                                "password", "pass1234",
                                "nickname", "앨리스",
                                "email", "alice@e.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void signup_duplicate_returns400WithMessage() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용 중인 아이디입니다."))
                .when(authService).signup(any());

        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice42",
                                "password", "pass1234",
                                "nickname", "앨리스"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }

    @Test
    void signup_serverError_returns500() throws Exception {
        doThrow(new RuntimeException("boom")).when(authService).signup(any());

        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice42",
                                "password", "pass1234",
                                "nickname", "앨리스"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_valid_returnsTokens() throws Exception {
        LoginResponse res = new LoginResponse(true, "로그인되었습니다.", sampleUser(), "access-tok");
        when(authService.login(any(), anyString(), any())).thenReturn(res);

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice", "password", "pass1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").value("access-tok"))
                .andExpect(jsonPath("$.user.id").value(10))
                .andExpect(jsonPath("$.user.username").value("alice"));
    }

    @Test
    void login_wrongPassword_returns400() throws Exception {
        when(authService.login(any(), anyString(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "아이디 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice", "password", "wrong"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void login_ipBlocked_returns429() throws Exception {
        when(authService.login(any(), anyString(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "로그인 시도 횟수를 초과했습니다. 120초 후에 다시 시도해주세요."));

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice", "password", "x"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("120초")));
    }

    @Test
    void login_usesXForwardedForForClientIp() throws Exception {
        when(authService.login(any(), eq("203.0.113.42"), any()))
                .thenReturn(new LoginResponse(true, "ok", sampleUser(), "tok"));

        mockMvc.perform(post("/api/login")
                        .header("X-Forwarded-For", "203.0.113.42, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice", "password", "pass1234"))))
                .andExpect(status().isOk());
        verify(authService).login(any(), eq("203.0.113.42"), any());
    }

    @Test
    void me_returnsAuthServiceResult() throws Exception {
        when(authService.getMe(any())).thenReturn(new MeResponse(true, sampleUser()));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.user.id").value(10));
    }

    @Test
    void forgotPassword_success_returnsOk() throws Exception {
        mockMvc.perform(post("/api/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice", "email", "alice@e.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void forgotPassword_userMismatch_returns400() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "아이디 또는 이메일이 일치하지 않습니다."))
                .when(authService).forgotPassword(any());

        mockMvc.perform(post("/api/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "bob", "email", "bob@e.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void resetPassword_valid_returnsOk() throws Exception {
        mockMvc.perform(post("/api/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "tok", "newPassword", "newpass12"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "인증이 만료됐어. 다시 시도해줘."))
                .when(authService).resetPassword(any());

        mockMvc.perform(post("/api/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "bad", "newPassword", "newpass12"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginUser", sampleUser());

        mockMvc.perform(post("/api/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(any(SessionUser.class), any());
    }

    @Test
    void refresh_valid_returnsNewTokens() throws Exception {
        when(authService.refresh("rt"))
                .thenReturn(new TokenRefreshResponse(true, "재발급", "new-access", "new-refresh"));

        mockMvc.perform(post("/api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "rt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh(anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "만료되거나 유효하지 않은 리프레시 토큰입니다."));

        mockMvc.perform(post("/api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "bad"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }
}
