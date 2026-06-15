package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.VideoActionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class VideoActionController {

    private final VideoActionService videoActionService;
    private final LoginUserResolver loginUserResolver;

    public VideoActionController(VideoActionService videoActionService,
                                 LoginUserResolver loginUserResolver) {
        this.videoActionService = videoActionService;
        this.loginUserResolver = loginUserResolver;
    }

    @PostMapping("/videos/{id}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long id, HttpSession session) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        try {
            LikeResponse response = videoActionService.toggleLike(id, sessionUser);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401) {
                return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @PostMapping("/videos/{id}/save")
    public ResponseEntity<?> toggleSave(@PathVariable Long id, HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        try {
            SaveResponse response = videoActionService.toggleSave(id, loginUserId);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401) {
                return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static class SimpleResponse {
        private boolean success;
        private String message;

        public SimpleResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class LikeResponse {
        private boolean success;
        private boolean liked;
        private long likeCount;

        public LikeResponse(boolean success, boolean liked, long likeCount) {
            this.success = success;
            this.liked = liked;
            this.likeCount = likeCount;
        }

        public boolean isSuccess() { return success; }
        public boolean isLiked() { return liked; }
        public long getLikeCount() { return likeCount; }
    }

    public static class SaveResponse {
        private boolean success;
        private boolean saved;

        public SaveResponse(boolean success, boolean saved) {
            this.success = success;
            this.saved = saved;
        }

        public boolean isSuccess() { return success; }
        public boolean isSaved() { return saved; }
    }
}