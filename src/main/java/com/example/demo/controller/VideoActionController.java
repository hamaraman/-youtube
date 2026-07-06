package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.VideoActionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

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

    @PostMapping("/videos/{id}/dislike")
    public ResponseEntity<?> toggleDislike(@PathVariable Long id, HttpSession session) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        try {
            DislikeResponse response = videoActionService.toggleDislike(id, sessionUser);
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

    @PostMapping("/videos/{id}/report")
    public ResponseEntity<?> reportVideo(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        Map<String, String> payload = body == null ? Map.of() : body;
        try {
            ReportResponse response = videoActionService.reportVideo(
                    id, payload.get("reason"), payload.get("detail"), sessionUser);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401) {
                return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
            }
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
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
        private boolean disliked;
        private long dislikeCount;

        public LikeResponse(boolean success, boolean liked, long likeCount) {
            this(success, liked, likeCount, false, 0);
        }

        public LikeResponse(boolean success, boolean liked, long likeCount, boolean disliked, long dislikeCount) {
            this.success = success;
            this.liked = liked;
            this.likeCount = likeCount;
            this.disliked = disliked;
            this.dislikeCount = dislikeCount;
        }

        public boolean isSuccess() { return success; }
        public boolean isLiked() { return liked; }
        public long getLikeCount() { return likeCount; }
        public boolean isDisliked() { return disliked; }
        public long getDislikeCount() { return dislikeCount; }
    }

    public static class DislikeResponse {
        private boolean success;
        private boolean disliked;
        private long dislikeCount;
        private boolean liked;
        private long likeCount;

        public DislikeResponse(boolean success, boolean disliked, long dislikeCount, boolean liked, long likeCount) {
            this.success = success;
            this.disliked = disliked;
            this.dislikeCount = dislikeCount;
            this.liked = liked;
            this.likeCount = likeCount;
        }

        public boolean isSuccess() { return success; }
        public boolean isDisliked() { return disliked; }
        public long getDislikeCount() { return dislikeCount; }
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

    public static class ReportResponse {
        private boolean success;
        private boolean alreadyReported;

        public ReportResponse(boolean success, boolean alreadyReported) {
            this.success = success;
            this.alreadyReported = alreadyReported;
        }

        public boolean isSuccess() { return success; }
        public boolean isAlreadyReported() { return alreadyReported; }
    }
}