package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

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
@Getter
@Setter
@NoArgsConstructor
public class VideoSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long videoId;

    private Long userId;
}