package com.example.demo.repository;

import com.example.demo.entity.VideoSave;
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
class VideoSaveRepositoryTest {

    @Autowired private VideoSaveRepository videoSaveRepository;

    @BeforeEach
    void setUp() {
        save(100L, 1L);
        save(100L, 2L);
        save(200L, 1L);
        save(300L, 1L);
    }

    private void save(Long videoId, Long userId) {
        VideoSave s = new VideoSave();
        s.setVideoId(videoId); s.setUserId(userId);
        videoSaveRepository.save(s);
    }

    @Test
    void existsByVideoIdAndUserId_returnsBoolean() {
        assertThat(videoSaveRepository.existsByVideoIdAndUserId(100L, 1L)).isTrue();
        assertThat(videoSaveRepository.existsByVideoIdAndUserId(100L, 999L)).isFalse();
    }

    @Test
    void findByUserIdOrderByIdDesc_returnsNewestFirst() {
        List<VideoSave> saves = videoSaveRepository.findByUserIdOrderByIdDesc(1L);
        assertThat(saves).extracting(VideoSave::getVideoId).containsExactly(300L, 200L, 100L);
    }

    @Test
    void findSavedVideoIdsByUserId_returnsMatching() {
        List<Long> ids = videoSaveRepository.findSavedVideoIdsByUserId(
                1L, List.of(100L, 200L, 300L, 999L));
        assertThat(ids).containsExactlyInAnyOrder(100L, 200L, 300L);
    }

    @Test
    void deleteByVideoId_removesAllSavesForVideo() {
        videoSaveRepository.deleteByVideoId(100L);
        videoSaveRepository.flush();

        assertThat(videoSaveRepository.existsByVideoIdAndUserId(100L, 1L)).isFalse();
        assertThat(videoSaveRepository.existsByVideoIdAndUserId(200L, 1L)).isTrue();
    }
}
