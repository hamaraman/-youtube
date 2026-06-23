package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 로컬 GPU 워커가 처리할 트랜스코딩 작업 큐.
 * 서버는 업로드된 원본을 MinIO에 올린 뒤 이 작업을 PENDING 상태로 쌓아두기만 하고,
 * 외부 워커(집 PC의 GPU)가 폴링하여 가져가 변환한다. PC가 꺼져 있으면 작업은 DB에 남아 대기한다.
 */
@Entity
@Table(name = "transcode_jobs",
       indexes = { @Index(name = "idx_tj_status", columnList = "status") })
@Getter
@Setter
@NoArgsConstructor
public class TranscodeJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long videoId;

    private String uuid;

    /** 변환 입력 원본의 MinIO 키 (예: videos/{uuid}.mp4) */
    @Column(columnDefinition = "TEXT")
    private String inputKey;

    /** 썸네일이 없어 워커가 자동 생성해야 하는지 여부 */
    private boolean needThumbnail;

    /** PENDING, CLAIMED, DONE, ERROR */
    @Column(nullable = false)
    private String status;

    private String workerId;

    private int attempts = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime claimedAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
