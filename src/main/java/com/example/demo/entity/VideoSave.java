package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "video_saves",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"videoId", "userId"})
        },
        indexes = {
                @Index(name = "idx_video_saves_video_id", columnList = "videoId"),
                @Index(name = "idx_video_saves_user_id", columnList = "userId")
        }
)
public class VideoSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long videoId;

    private Long userId;

    public VideoSave() {
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}