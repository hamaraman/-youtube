package com.example.demo.repository;

import com.example.demo.entity.PlaylistVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlaylistVideoRepository extends JpaRepository<PlaylistVideo, Long> {
    List<PlaylistVideo> findByPlaylistIdOrderByAddedAtDesc(Long playlistId);
    boolean existsByPlaylistIdAndVideoId(Long playlistId, Long videoId);
    Optional<PlaylistVideo> findByPlaylistIdAndVideoId(Long playlistId, Long videoId);
    void deleteByPlaylistId(Long playlistId);
    long countByPlaylistId(Long playlistId);
}
