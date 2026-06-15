package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.Duration;

@Entity
@Table(name = "videos",
       indexes = {
               @Index(name = "idx_videos_owner_id", columnList = "ownerId"),
               @Index(name = "idx_videos_visibility", columnList = "visibility"),
               @Index(name = "idx_videos_category", columnList = "category")
       })
@Getter
@Setter
@NoArgsConstructor
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String channel;

    private String avatar;

    private String category;

    @Column(nullable = false)
    private String duration;

    private String visibility;

    @Column(columnDefinition = "TEXT")
    private String embedUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String thumbnail;

    @Column(columnDefinition = "TEXT")
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String videoUrl1080;

    @Column(columnDefinition = "TEXT")
    private String videoUrl720;

    @Column(columnDefinition = "TEXT")
    private String videoUrl480;

    @Column(columnDefinition = "TEXT")
    private String videoUrl360;

    private String dateText;

    @Column(name = "view_count")
    private long viewCount = 0;

    @Column
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getDateText() {
        if (createdAt != null) return timeAgo(createdAt);
        return dateText;
    }

    private static String timeAgo(LocalDateTime time) {
        Duration d = Duration.between(time, LocalDateTime.now());
        long minutes = d.toMinutes();
        if (minutes < 1) return "방금 전";
        if (minutes < 60) return minutes + "분 전";
        long hours = d.toHours();
        if (hours < 24) return hours + "시간 전";
        long days = d.toDays();
        if (days < 7) return days + "일 전";
        if (days < 30) return (days / 7) + "주 전";
        if (days < 365) return (days / 30) + "개월 전";
        return (days / 365) + "년 전";
    }
}