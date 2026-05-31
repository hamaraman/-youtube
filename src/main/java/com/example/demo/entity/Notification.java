package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "related_video_id")
    private Long relatedVideoId;

    @Column(length = 500)
    private String thumbnail;

    @Column(length = 20)
    private String type;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getReceiverId() { return receiverId; }
    public String getMessage() { return message; }
    public Long getRelatedVideoId() { return relatedVideoId; }
    public String getThumbnail() { return thumbnail; }
    public String getType() { return type; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public void setMessage(String message) { this.message = message; }
    public void setRelatedVideoId(Long relatedVideoId) { this.relatedVideoId = relatedVideoId; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public void setType(String type) { this.type = type; }
    public void setRead(boolean read) { this.read = read; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
