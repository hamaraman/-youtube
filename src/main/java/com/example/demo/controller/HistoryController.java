package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoHistory;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.VideoHistoryRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api")
public class HistoryController {

    private static final String ANON_VIEWED_SESSION_KEY = "anonViewedVideoTimestamps";

    private final VideoHistoryRepository videoHistoryRepository;
    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final CommentRepository commentRepository;
    private final LoginUserResolver loginUserResolver;

    public HistoryController(
            VideoHistoryRepository videoHistoryRepository,
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoSaveRepository videoSaveRepository,
            CommentRepository commentRepository,
            LoginUserResolver loginUserResolver
    ) {
        this.videoHistoryRepository = videoHistoryRepository;
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.commentRepository = commentRepository;
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

        long now = System.currentTimeMillis();
        long cooldownMs = 24 * 60 * 60 * 1000L;

        if (loginUserId == null) {
            @SuppressWarnings("unchecked")
            Map<Long, Long> viewedAt = (Map<Long, Long>) session.getAttribute(ANON_VIEWED_SESSION_KEY);
            if (viewedAt == null) {
                viewedAt = new HashMap<>();
            }

            Long lastViewedAt = viewedAt.get(id);
            boolean isNew = lastViewedAt == null;
            boolean isExpired = lastViewedAt != null && now - lastViewedAt > cooldownMs;

            if (isNew || isExpired) {
                video.setViewCount(video.getViewCount() + 1);
                videoRepository.save(video);
            }

            viewedAt.put(id, now);
            session.setAttribute(ANON_VIEWED_SESSION_KEY, viewedAt);

            return ResponseEntity.ok(new SimpleResponse(true, "조회수 처리됨"));
        }

        Optional<VideoHistory> existing = videoHistoryRepository.findByVideoIdAndUserId(id, loginUserId);
        boolean isNew = existing.isEmpty();
        boolean isExpired = existing.map(h -> now - h.getWatchedAt() > cooldownMs).orElse(false);

        VideoHistory history = existing.orElseGet(VideoHistory::new);
        history.setVideoId(id);
        history.setUserId(loginUserId);
        history.setWatchedAt(now);
        videoHistoryRepository.save(history);

        if (isNew || isExpired) {
            video.setViewCount(video.getViewCount() + 1);
            videoRepository.save(video);
        }

        return ResponseEntity.ok(new SimpleResponse(true, "시청 기록 저장됨"));
    }

    @PutMapping("/videos/{id}/progress")
    public ResponseEntity<?> saveProgress(@PathVariable Long id, @RequestBody Map<String, Double> body, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) return ResponseEntity.status(401).build();

        Double position = body.get("position");
        if (position == null || position < 0) return ResponseEntity.badRequest().build();

        Optional<VideoHistory> existing = videoHistoryRepository.findByVideoIdAndUserId(id, loginUserId);
        VideoHistory history = existing.orElseGet(() -> {
            VideoHistory h = new VideoHistory();
            h.setVideoId(id);
            h.setUserId(loginUserId);
            h.setWatchedAt(System.currentTimeMillis());
            return h;
        });
        history.setLastPosition(position);
        videoHistoryRepository.save(history);

        return ResponseEntity.ok(new SimpleResponse(true, "저장됨"));
    }

    @GetMapping("/videos/{id}/progress")
    public ResponseEntity<?> getProgress(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) return ResponseEntity.ok(Map.of("position", 0.0));

        Optional<VideoHistory> existing = videoHistoryRepository.findByVideoIdAndUserId(id, loginUserId);
        double position = existing
                .map(h -> h.getLastPosition() != null ? h.getLastPosition() : 0.0)
                .orElse(0.0);
        return ResponseEntity.ok(Map.of("position", position));
    }

    @GetMapping("/my-progress")
    public ResponseEntity<?> getMyProgress(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) return ResponseEntity.ok(Map.of());

        Map<Long, Double> result = new HashMap<>();
        for (VideoHistory h : videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(loginUserId)) {
            if (h.getLastPosition() != null && h.getLastPosition() > 0) {
                result.put(h.getVideoId(), h.getLastPosition());
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-history")
    public ResponseEntity<?> getMyHistory(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<Long> videoIds = videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(loginUserId)
                .stream()
                .map(VideoHistory::getVideoId)
                .collect(Collectors.toList());

        if (videoIds.isEmpty()) return ResponseEntity.ok(List.of());

        Map<Long, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        List<Video> videos = videoIds.stream()
                .map(videoMap::get)
                .filter(v -> v != null)
                .filter(v -> !"비공개".equals(v.getVisibility()) || loginUserId.equals(v.getOwnerId()))
                .collect(Collectors.toList());

        if (videos.isEmpty()) return ResponseEntity.ok(List.of());

        List<Long> ids = videos.stream().map(Video::getId).collect(Collectors.toList());
        Map<Long, Long> likeCounts = toCountMap(videoLikeRepository.countByVideoIdIn(ids));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByVideoIdIn(ids));
        Set<Long> likedSet = new HashSet<>(videoLikeRepository.findLikedVideoIdsByUserId(loginUserId, ids));
        Set<Long> savedSet = new HashSet<>(videoSaveRepository.findSavedVideoIdsByUserId(loginUserId, ids));

        List<VideoController.VideoItem> result = videos.stream()
                .map(v -> VideoController.VideoItem.from(v,
                        likeCounts.getOrDefault(v.getId(), 0L),
                        commentCounts.getOrDefault(v.getId(), 0L),
                        likedSet.contains(v.getId()),
                        savedSet.contains(v.getId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
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
