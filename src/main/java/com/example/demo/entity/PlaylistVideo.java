package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "playlist_videos")
public class PlaylistVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long playlistId;
    private Long videoId;

    @Column
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) addedAt = LocalDateTime.now();
    }

    public PlaylistVideo() {}

    public Long getId() { return id; }
    public Long getPlaylistId() { return playlistId; }
    public Long getVideoId() { return videoId; }
    public LocalDateTime getAddedAt() { return addedAt; }

    public void setId(Long id) { this.id = id; }
    public void setPlaylistId(Long playlistId) { this.playlistId = playlistId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
}
