package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoSave;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class VideoActionController {

    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final LoginUserResolver loginUserResolver;

    public VideoActionController(
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoSaveRepository videoSaveRepository,
            LoginUserResolver loginUserResolver
    ) {
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.loginUserResolver = loginUserResolver;
    }

    @PostMapping("/videos/{id}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<VideoLike> existing = videoLikeRepository.findByVideoIdAndUserId(id, loginUserId);

        boolean liked;
        if (existing.isPresent()) {
            videoLikeRepository.delete(existing.get());
            liked = false;
        } else {
            VideoLike videoLike = new VideoLike();
            videoLike.setVideoId(id);
            videoLike.setUserId(loginUserId);
            videoLikeRepository.save(videoLike);
            liked = true;
        }

        long likeCount = videoLikeRepository.countByVideoId(id);

        return ResponseEntity.ok(new LikeResponse(true, liked, likeCount));
    }

    @PostMapping("/videos/{id}/save")
    public ResponseEntity<?> toggleSave(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<VideoSave> existing = videoSaveRepository.findByVideoIdAndUserId(id, loginUserId);

        boolean saved;
        if (existing.isPresent()) {
            videoSaveRepository.delete(existing.get());
            saved = false;
        } else {
            VideoSave videoSave = new VideoSave();
            videoSave.setVideoId(id);
            videoSave.setUserId(loginUserId);
            videoSaveRepository.save(videoSave);
            saved = true;
        }

        return ResponseEntity.ok(new SaveResponse(true, saved));
    }

    private Long getLoginUserId(HttpSession session) {
        return loginUserResolver.getUserId(session);
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