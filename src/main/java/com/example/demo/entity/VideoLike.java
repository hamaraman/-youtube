package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "video_likes",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"videoId", "userId"})
        },
        indexes = {
                @Index(name = "idx_video_likes_video_id", columnList = "videoId"),
                @Index(name = "idx_video_likes_user_id", columnList = "userId")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VideoLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long videoId;

    private Long userId;
}