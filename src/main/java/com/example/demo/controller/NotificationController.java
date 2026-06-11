package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.NotificationService;
import com.example.demo.entity.Notification;
import com.example.demo.repository.NotificationRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final LoginUserResolver loginUserResolver;
    private final NotificationService notificationService;

    public NotificationController(NotificationRepository notificationRepository,
                                  LoginUserResolver loginUserResolver,
                                  NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
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

        List<Notification> notifications = notificationRepository
                .findByReceiverIdOrderByCreatedAtDesc(userId);
        long unreadCount = notificationRepository.countByReceiverIdAndReadFalse(userId);

        return ResponseEntity.ok(Map.of(
                "notifications", notifications,
                "unreadCount", unreadCount
        ));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getReceiverId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        List<Notification> unread = notificationRepository
                .findByReceiverIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(n -> !n.isRead())
                .toList();

        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getReceiverId().equals(userId)) {
                notificationRepository.delete(n);
            }
        });
        return ResponseEntity.ok().build();
    }
}
