package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.Duration;

@Entity
@Table(
        name = "video_reports",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"videoId", "reporterId"})
        },
        indexes = {
                @Index(name = "idx_video_reports_video_id", columnList = "videoId")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VideoReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long videoId;

    private Long reporterId;

    @Column(nullable = false)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getTime() {
        if (createdAt == null) return "";
        Duration d = Duration.between(createdAt, LocalDateTime.now());
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
