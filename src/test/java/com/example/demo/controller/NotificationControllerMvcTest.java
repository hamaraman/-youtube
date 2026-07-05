package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.entity.Notification;
import com.example.demo.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class NotificationControllerMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationService notificationService;
    @MockitoBean private LoginUserResolver loginUserResolver;

    @Test
    void stream_notLoggedIn_returns401WithoutSubscribing() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(get("/api/notifications/stream").accept("text/event-stream"))
                .andExpect(status().isUnauthorized());
        verify(notificationService, never()).subscribe(any());
    }

    @Test
    void stream_loggedIn_delegatesToService() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(notificationService.subscribe(10L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/notifications/stream").accept("text/event-stream"))
                .andExpect(status().isOk());
        verify(notificationService).subscribe(10L);
    }

    @Test
    void getNotifications_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
        verify(notificationService, never()).getNotifications(any());
    }

    @Test
    void getNotifications_loggedIn_returnsPayload() throws Exception {
        Notification n = new Notification();
        n.setId(1L); n.setReceiverId(10L); n.setMessage("hi"); n.setType("LIKE");
        when(loginUserResolver.getUserId(any())).thenReturn(10L);
        when(notificationService.getNotifications(10L))
                .thenReturn(Map.of("notifications", List.of(n), "unreadCount", 3L));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.notifications[0].id").value(1));
    }

    @Test
    void markRead_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(post("/api/notifications/1/read"))
                .andExpect(status().isUnauthorized());
        verify(notificationService, never()).markRead(any(), any());
    }

    @Test
    void markRead_loggedIn_delegates() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(post("/api/notifications/1/read"))
                .andExpect(status().isOk());
        verify(notificationService).markRead(1L, 10L);
    }

    @Test
    void markAllRead_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAllRead_loggedIn_delegates() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk());
        verify(notificationService).markAllRead(10L);
    }

    @Test
    void deleteNotification_notLoggedIn_returns401() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);

        mockMvc.perform(delete("/api/notifications/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteNotification_loggedIn_delegates() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(10L);

        mockMvc.perform(delete("/api/notifications/1"))
                .andExpect(status().isOk());
        verify(notificationService).deleteNotification(1L, 10L);
    }
}
