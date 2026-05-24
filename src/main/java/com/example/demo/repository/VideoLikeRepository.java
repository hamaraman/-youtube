package com.example.demo.repository;

import com.example.demo.entity.VideoLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoLikeRepository extends JpaRepository<VideoLike, Long> {
    long countByVideoId(Long videoId);
    boolean existsByVideoIdAndUserId(Long videoId, Long userId);
    Optional<VideoLike> findByVideoIdAndUserId(Long videoId, Long userId);
    List<VideoLike> findByUserIdOrderByIdDesc(Long userId);
    void deleteByVideoId(Long videoId);
}