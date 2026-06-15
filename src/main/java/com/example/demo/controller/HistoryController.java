package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.HistoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HistoryController {

    private static final String ANON_VIEWED_SESSION_KEY = "anonViewedVideoTimestamps";

    private final HistoryService historyService;
    private final LoginUserResolver loginUserResolver;

    public HistoryController(HistoryService historyService, LoginUserResolver loginUserResolver) {
        this.historyService = historyService;
        this.loginUserResolver = loginUserResolver;
    }

    @PostMapping("/videos/{id}/history")
    public ResponseEntity<?> markHistory(@PathVariable Long id, HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);

        @SuppressWarnings("unchecked")
        Map<Long, Long> viewedAt = (Map<Long, Long>) session.getAttribute(ANON_VIEWED_SESSION_KEY);
        if (viewedAt == null) {
            viewedAt = new HashMap<>();
        }

        try {
            historyService.markHistory(id, loginUserId, viewedAt);
            if (loginUserId == null) {
                session.setAttribute(ANON_VIEWED_SESSION_KEY, viewedAt);
                return ResponseEntity.ok(new SimpleResponse(true, "조회수 처리됨"));
            }
            return ResponseEntity.ok(new SimpleResponse(true, "시청 기록 저장됨"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @PutMapping("/videos/{id}/progress")
    public ResponseEntity<?> saveProgress(@PathVariable Long id, @RequestBody Map<String, Double> body, HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        if (loginUserId == null) return ResponseEntity.status(401).build();

        Double position = body.get("position");
        try {
            historyService.saveProgress(id, position, loginUserId);
            return ResponseEntity.ok(new SimpleResponse(true, "저장됨"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @GetMapping("/videos/{id}/progress")
    public ResponseEntity<?> getProgress(@PathVariable Long id, HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        double position = historyService.getProgress(id, loginUserId);
        return ResponseEntity.ok(Map.of("position", position));
    }

    @GetMapping("/my-progress")
    public ResponseEntity<?> getMyProgress(HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        return ResponseEntity.ok(historyService.getMyProgress(loginUserId));
    }

    @GetMapping("/my-history")
    public ResponseEntity<?> getMyHistory(HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        return ResponseEntity.ok(historyService.getMyHistory(loginUserId));
    }

    public static class SimpleResponse {
        private boolean success;
        private String message;

        public SimpleResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
