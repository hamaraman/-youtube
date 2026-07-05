package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.NotificationService;
import jakarta.servlet.http.HttpServletResponse;
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
    public SseEmitter stream(HttpSession session, HttpServletResponse response) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) {
            // 미인증: 예외를 MVC 예외 파이프라인에 던지지 않고 401로 조용히 종료.
            // completeWithError(...)를 쓰면 GlobalExceptionHandler가 Map 에러바디를
            // text/event-stream으로 직렬화하려다 HttpMessageNotWritableException 경고를
            // 남기므로(로그아웃/세션만료 브라우저가 붙을 때마다 발생) 사용하지 않음.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }
        // Nginx(및 프록시)가 SSE 응답을 버퍼링하지 않도록 강제 — 이게 없으면
        // 기본 proxy_buffering on 때문에 connected/heartbeat가 갇혀 클라이언트가 0바이트를 받음
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
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
