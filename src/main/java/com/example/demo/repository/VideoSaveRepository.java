package com.example.demo.repository;

import com.example.demo.entity.VideoSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoSaveRepository extends JpaRepository<VideoSave, Long> {
    boolean existsByVideoIdAndUserId(Long videoId, Long userId);
    Optional<VideoSave> findByVideoIdAndUserId(Long videoId, Long userId);
    List<VideoSave> findByUserIdOrderByIdDesc(Long userId);
    void deleteByVideoId(Long videoId);

    @Query("SELECT v.videoId FROM VideoSave v WHERE v.userId = :userId AND v.videoId IN :videoIds")
    List<Long> findSavedVideoIdsByUserId(@Param("userId") Long userId, @Param("videoIds") List<Long> videoIds);
}
