package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.Duration;

@Entity
@Table(name = "comments",
       indexes = {
               @Index(name = "idx_comments_video_id", columnList = "videoId"),
               @Index(name = "idx_comments_parent_id", columnList = "parent_id")
       })
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long videoId;

    private Long userId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column
    private String time;

    @Column
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getTime() {
        if (createdAt != null) return timeAgo(createdAt);
        return time;
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