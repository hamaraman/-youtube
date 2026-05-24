package com.example.demo.repository;

import com.example.demo.entity.VideoHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoHistoryRepository extends JpaRepository<VideoHistory, Long> {
    Optional<VideoHistory> findByVideoIdAndUserId(Long videoId, Long userId);
    List<VideoHistory> findByUserIdOrderByWatchedAtDesc(Long userId);
    void deleteByVideoId(Long videoId);
}