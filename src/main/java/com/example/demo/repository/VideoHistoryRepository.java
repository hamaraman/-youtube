package com.example.demo.repository;

import com.example.demo.entity.VideoHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoHistoryRepository extends JpaRepository<VideoHistory, Long> {
    Optional<VideoHistory> findByVideoIdAndUserId(Long videoId, Long userId);
    List<VideoHistory> findByUserIdOrderByWatchedAtDesc(Long userId);
    void deleteByVideoId(Long videoId);

    @Query("SELECT h FROM VideoHistory h WHERE h.videoId IN :videoIds AND h.watchedAt >= :since")
    List<VideoHistory> findByVideoIdInAndWatchedAtSince(
            @Param("videoIds") List<Long> videoIds,
            @Param("since") long since);
}