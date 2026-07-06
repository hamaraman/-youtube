package com.example.demo.repository;

import com.example.demo.entity.VideoReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoReportRepository extends JpaRepository<VideoReport, Long> {
    boolean existsByVideoIdAndReporterId(Long videoId, Long reporterId);
    List<VideoReport> findAllByOrderByCreatedAtDesc();
    void deleteByVideoId(Long videoId);
}
