package com.example.demo.repository;

import com.example.demo.entity.VideoLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoLikeRepository extends JpaRepository<VideoLike, Long> {
    long countByVideoId(Long videoId);
    boolean existsByVideoIdAndUserId(Long videoId, Long userId);
    Optional<VideoLike> findByVideoIdAndUserId(Long videoId, Long userId);
    List<VideoLike> findByUserIdOrderByIdDesc(Long userId);
    void deleteByVideoId(Long videoId);

    @Query("SELECT v.videoId, COUNT(v) FROM VideoLike v WHERE v.videoId IN :videoIds GROUP BY v.videoId")
    List<Object[]> countByVideoIdIn(@Param("videoIds") List<Long> videoIds);

    @Query("SELECT v.videoId FROM VideoLike v WHERE v.userId = :userId AND v.videoId IN :videoIds")
    List<Long> findLikedVideoIdsByUserId(@Param("userId") Long userId, @Param("videoIds") List<Long> videoIds);
}
