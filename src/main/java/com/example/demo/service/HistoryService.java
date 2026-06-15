package com.example.demo.service;

import com.example.demo.controller.VideoController.VideoItem;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoHistory;
import com.example.demo.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private final VideoHistoryRepository videoHistoryRepository;
    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final CommentRepository commentRepository;

    public HistoryService(
            VideoHistoryRepository videoHistoryRepository,
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoSaveRepository videoSaveRepository,
            CommentRepository commentRepository
    ) {
        this.videoHistoryRepository = videoHistoryRepository;
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.commentRepository = commentRepository;
    }

    public void markHistory(Long videoId, Long loginUserId, Map<Long, Long> anonViewedAt) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        long now = System.currentTimeMillis();
        long cooldownMs = 24 * 60 * 60 * 1000L;

        if (loginUserId == null) {
            Long lastViewedAt = anonViewedAt.get(videoId);
            boolean isNew = lastViewedAt == null;
            boolean isExpired = lastViewedAt != null && now - lastViewedAt > cooldownMs;

            if (isNew || isExpired) {
                video.setViewCount(video.getViewCount() + 1);
                videoRepository.save(video);
            }
            anonViewedAt.put(videoId, now);
            return;
        }

        Optional<VideoHistory> existing = videoHistoryRepository.findByVideoIdAndUserId(videoId, loginUserId);
        boolean isNew = existing.isEmpty();
        boolean isExpired = existing.map(h -> now - h.getWatchedAt() > cooldownMs).orElse(false);

        VideoHistory history = existing.orElseGet(VideoHistory::new);
        history.setVideoId(videoId);
        history.setUserId(loginUserId);
        history.setWatchedAt(now);
        videoHistoryRepository.save(history);

        if (isNew || isExpired) {
            video.setViewCount(video.getViewCount() + 1);
            videoRepository.save(video);
        }
    }

    public void saveProgress(Long videoId, Double position, Long loginUserId) {
        if (position == null || position < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 진행 위치입니다.");
        }

        Optional<VideoHistory> existing = videoHistoryRepository.findByVideoIdAndUserId(videoId, loginUserId);
        VideoHistory history = existing.orElseGet(() -> {
            VideoHistory h = new VideoHistory();
            h.setVideoId(videoId);
            h.setUserId(loginUserId);
            h.setWatchedAt(System.currentTimeMillis());
            return h;
        });
        history.setLastPosition(position);
        videoHistoryRepository.save(history);
    }

    public double getProgress(Long videoId, Long loginUserId) {
        if (loginUserId == null) return 0.0;

        Optional<VideoHistory> existing = videoHistoryRepository.findByVideoIdAndUserId(videoId, loginUserId);
        return existing
                .map(h -> h.getLastPosition() != null ? h.getLastPosition() : 0.0)
                .orElse(0.0);
    }

    public Map<Long, Double> getMyProgress(Long loginUserId) {
        if (loginUserId == null) return Map.of();

        Map<Long, Double> result = new HashMap<>();
        for (VideoHistory h : videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(loginUserId)) {
            if (h.getLastPosition() != null && h.getLastPosition() > 0) {
                result.put(h.getVideoId(), h.getLastPosition());
            }
        }
        return result;
    }

    public List<VideoItem> getMyHistory(Long loginUserId) {
        List<Long> videoIds = videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(loginUserId)
                .stream()
                .map(VideoHistory::getVideoId)
                .collect(Collectors.toList());

        if (videoIds.isEmpty()) return List.of();

        Map<Long, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        List<Video> videos = videoIds.stream()
                .map(videoMap::get)
                .filter(v -> v != null)
                .filter(v -> !"비공개".equals(v.getVisibility()) || loginUserId.equals(v.getOwnerId()))
                .collect(Collectors.toList());

        if (videos.isEmpty()) return List.of();

        List<Long> ids = videos.stream().map(Video::getId).collect(Collectors.toList());
        Map<Long, Long> likeCounts = toCountMap(videoLikeRepository.countByVideoIdIn(ids));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByVideoIdIn(ids));
        Set<Long> likedSet = new HashSet<>(videoLikeRepository.findLikedVideoIdsByUserId(loginUserId, ids));
        Set<Long> savedSet = new HashSet<>(videoSaveRepository.findSavedVideoIdsByUserId(loginUserId, ids));

        return videos.stream()
                .map(v -> VideoItem.from(v,
                        likeCounts.getOrDefault(v.getId(), 0L),
                        commentCounts.getOrDefault(v.getId(), 0L),
                        likedSet.contains(v.getId()),
                        savedSet.contains(v.getId())))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
