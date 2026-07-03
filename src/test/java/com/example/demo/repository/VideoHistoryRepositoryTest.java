package com.example.demo.repository;

import com.example.demo.entity.VideoHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class VideoHistoryRepositoryTest {

    @Autowired private VideoHistoryRepository videoHistoryRepository;

    private static final long HOUR_MS = 60L * 60 * 1000;
    private long now;

    @BeforeEach
    void setUp() {
        now = System.currentTimeMillis();
        record(100L, 1L, now - HOUR_MS, 30.0);
        record(200L, 1L, now - 2 * HOUR_MS, 60.0);
        record(300L, 1L, now - 3 * HOUR_MS, 90.0);
        record(100L, 2L, now - 30 * 60 * 1000, 10.0);
    }

    private void record(Long videoId, Long userId, long watchedAt, Double position) {
        VideoHistory h = new VideoHistory();
        h.setVideoId(videoId); h.setUserId(userId);
        h.setWatchedAt(watchedAt); h.setLastPosition(position);
        videoHistoryRepository.save(h);
    }

    @Test
    void findByVideoIdAndUserId_returnsOptional() {
        assertThat(videoHistoryRepository.findByVideoIdAndUserId(100L, 1L)).isPresent();
        assertThat(videoHistoryRepository.findByVideoIdAndUserId(100L, 999L)).isEmpty();
    }

    @Test
    void findByUserIdOrderByWatchedAtDesc_newestFirst() {
        List<VideoHistory> history = videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(1L);
        assertThat(history).extracting(VideoHistory::getVideoId)
                .containsExactly(100L, 200L, 300L);
    }

    @Test
    void findByUserId_isolatesUsers() {
        List<VideoHistory> user2 = videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(2L);
        assertThat(user2).hasSize(1);
        assertThat(user2.get(0).getVideoId()).isEqualTo(100L);
    }

    @Test
    void findByVideoIdInAndWatchedAtSince_appliesTimeCutoff() {
        long cutoff = now - (HOUR_MS + HOUR_MS / 2); // 1.5시간 전
        List<VideoHistory> recent = videoHistoryRepository.findByVideoIdInAndWatchedAtSince(
                List.of(100L, 200L, 300L), cutoff);
        assertThat(recent).extracting(VideoHistory::getVideoId)
                .containsExactlyInAnyOrder(100L, 100L);
    }

    @Test
    void deleteByVideoId_removesAllHistoryForVideo() {
        videoHistoryRepository.deleteByVideoId(100L);
        videoHistoryRepository.flush();

        assertThat(videoHistoryRepository.findByVideoIdAndUserId(100L, 1L)).isEmpty();
        assertThat(videoHistoryRepository.findByVideoIdAndUserId(100L, 2L)).isEmpty();
        assertThat(videoHistoryRepository.findByVideoIdAndUserId(200L, 1L)).isPresent();
    }
}
