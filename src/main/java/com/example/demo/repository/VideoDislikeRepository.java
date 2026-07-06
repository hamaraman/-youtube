package com.example.demo.repository;

import com.example.demo.entity.VideoDislike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoDislikeRepository extends JpaRepository<VideoDislike, Long> {
    long countByVideoId(Long videoId);
    boolean existsByVideoIdAndUserId(Long videoId, Long userId);
    Optional<VideoDislike> findByVideoIdAndUserId(Long videoId, Long userId);
    void deleteByVideoId(Long videoId);
}
