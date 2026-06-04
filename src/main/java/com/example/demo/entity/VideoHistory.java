package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "video_history",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"videoId", "userId"})
        },
        indexes = {
                @Index(name = "idx_video_history_user_id", columnList = "userId")
        }
)
public class VideoHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long videoId;

    private Long userId;

    private Long watchedAt;

    public VideoHistory() {
    }

    public Long getId() {
        return id;
    }

    public Long getVideoId() {
        return videoId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getWatchedAt() {
        return watchedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setWatchedAt(Long watchedAt) {
        this.watchedAt = watchedAt;
    }
}