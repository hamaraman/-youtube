package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoHistory;
import com.example.demo.repository.VideoHistoryRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class HistoryController {

    private final VideoHistoryRepository videoHistoryRepository;
    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final LoginUserResolver loginUserResolver;

    public HistoryController(
            VideoHistoryRepository videoHistoryRepository,
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoSaveRepository videoSaveRepository,
            LoginUserResolver loginUserResolver
    ) {
        this.videoHistoryRepository = videoHistoryRepository;
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.loginUserResolver = loginUserResolver;
    }

    @PostMapping("/videos/{id}/history")
    public ResponseEntity<?> markHistory(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);

        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Video video = optionalVideo.get();

        if (loginUserId == null) {
            video.setViewCount(video.getViewCount() + 1);
            videoRepository.save(video);
            return ResponseEntity.ok(new SimpleResponse(true, "조회수 증가"));
        }

        Optional<VideoHistory> existing = videoHistoryRepository.findByVideoIdAndUserId(id, loginUserId);
        boolean isNew = existing.isEmpty();

        VideoHistory history = existing.orElseGet(VideoHistory::new);
        history.setVideoId(id);
        history.setUserId(loginUserId);
        history.setWatchedAt(System.currentTimeMillis());
        videoHistoryRepository.save(history);

        if (isNew) {
            video.setViewCount(video.getViewCount() + 1);
            videoRepository.save(video);
        }

        return ResponseEntity.ok(new SimpleResponse(true, "시청 기록 저장됨"));
    }

    @GetMapping("/my-history")
    public ResponseEntity<?> getMyHistory(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<VideoController.VideoItem> result = videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(loginUserId)
                .stream()
                .map(VideoHistory::getVideoId)
                .map(videoRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(video -> !"비공개".equals(video.getVisibility()) || loginUserId.equals(video.getOwnerId()))
                .map(video -> VideoController.VideoItem.from(
                        video,
                        videoLikeRepository.countByVideoId(video.getId()),
                        videoLikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId),
                        videoSaveRepository.existsByVideoIdAndUserId(video.getId(), loginUserId)
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
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

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}