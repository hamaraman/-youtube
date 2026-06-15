package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final LoginUserResolver loginUserResolver;
    private final NotificationService notificationService;

    public NotificationController(LoginUserResolver loginUserResolver,
                                  NotificationService notificationService) {
        this.loginUserResolver = loginUserResolver;
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new IllegalStateException("로그인이 필요합니다."));
            return emitter;
        }
        return notificationService.subscribe(userId);
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();
        notificationService.markRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.ok().build();
    }
}
