package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.SubscriptionController.SubscribeResponse;
import com.example.demo.service.SubscriptionService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SubscriptionController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SubscriptionControllerMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SubscriptionService subscriptionService;
    @MockitoBean private LoginUserResolver loginUserResolver;

    private SessionUser me() {
        return new SessionUser(10L, "user", "닉", "e@e.com", "채널", "img", "USER");
    }

    @Test
    void toggleSubscribe_success_returnsResponse() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(subscriptionService.toggleSubscribe(eq(5L), any(SessionUser.class)))
                .thenReturn(new SubscribeResponse(true, true, 42L));

        mockMvc.perform(post("/api/users/5/subscribe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(42));
    }

    @Test
    void toggleSubscribe_notLoggedIn_returns401WithBody() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(null);
        when(subscriptionService.toggleSubscribe(eq(5L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        mockMvc.perform(post("/api/users/5/subscribe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.subscribed").value(false))
                .andExpect(jsonPath("$.subscriberCount").value(0));
    }

    @Test
    void toggleSubscribe_selfSubscribe_returns400WithBody() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(subscriptionService.toggleSubscribe(eq(10L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신을 구독할 수 없습니다."));

        mockMvc.perform(post("/api/users/10/subscribe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void searchChannels_blankKeyword_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/users/search").param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
        verify(subscriptionService, never()).searchChannels(any(), any());
    }

    @Test
    void searchChannels_returnsResults() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(subscriptionService.searchChannels("bob", 10L))
                .thenReturn(List.of(Map.of(
                        "id", 5L, "channelName", "Bob", "subscribed", false)));

        mockMvc.perform(get("/api/users/search").param("keyword", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].channelName").value("Bob"));
    }

    @Test
    void getMySubscriptions_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(get("/api/users/me/subscriptions"))
                .andExpect(status().isUnauthorized());
        verify(subscriptionService, never()).getMySubscriptions(any());
    }

    @Test
    void getMySubscriptions_loggedIn_returnsList() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(subscriptionService.getMySubscriptions(10L))
                .thenReturn(List.of(Map.of(
                        "channelOwnerId", 5L, "channelName", "Bob", "profileImage", "img")));

        mockMvc.perform(get("/api/users/me/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channelOwnerId").value(5))
                .andExpect(jsonPath("$[0].channelName").value("Bob"));
    }

    @Test
    void getSubscriptionStatus_returnsResponse() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(subscriptionService.getSubscriptionStatus(5L, 10L))
                .thenReturn(new SubscribeResponse(true, true, 100L));

        mockMvc.perform(get("/api/users/5/subscription-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(100));
    }
}
