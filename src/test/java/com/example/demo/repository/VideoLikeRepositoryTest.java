package com.example.demo.repository;

import com.example.demo.entity.VideoLike;
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
class VideoLikeRepositoryTest {

    @Autowired private VideoLikeRepository videoLikeRepository;

    @BeforeEach
    void setUp() {
        like(100L, 1L);
        like(100L, 2L);
        like(100L, 3L);
        like(200L, 1L);
        like(300L, 2L);
    }

    private VideoLike like(Long videoId, Long userId) {
        VideoLike l = new VideoLike();
        l.setVideoId(videoId); l.setUserId(userId);
        return videoLikeRepository.save(l);
    }

    @Test
    void countByVideoId_returnsCount() {
        assertThat(videoLikeRepository.countByVideoId(100L)).isEqualTo(3L);
        assertThat(videoLikeRepository.countByVideoId(200L)).isEqualTo(1L);
        assertThat(videoLikeRepository.countByVideoId(999L)).isZero();
    }

    @Test
    void existsByVideoIdAndUserId_returnsBoolean() {
        assertThat(videoLikeRepository.existsByVideoIdAndUserId(100L, 1L)).isTrue();
        assertThat(videoLikeRepository.existsByVideoIdAndUserId(100L, 999L)).isFalse();
    }

    @Test
    void findByVideoIdAndUserId_returnsOptional() {
        assertThat(videoLikeRepository.findByVideoIdAndUserId(100L, 1L)).isPresent();
        assertThat(videoLikeRepository.findByVideoIdAndUserId(100L, 999L)).isEmpty();
    }

    @Test
    void findByUserIdOrderByIdDesc_returnsUserLikes() {
        List<VideoLike> user1 = videoLikeRepository.findByUserIdOrderByIdDesc(1L);
        assertThat(user1).extracting(VideoLike::getVideoId)
                .containsExactly(200L, 100L);
    }

    @Test
    void countByVideoIdIn_returnsGroupedCounts() {
        List<Object[]> rows = videoLikeRepository.countByVideoIdIn(List.of(100L, 200L, 300L, 999L));
        assertThat(rows).hasSize(3);
        for (Object[] row : rows) {
            Long videoId = (Long) row[0];
            Long count = (Long) row[1];
            if (videoId == 100L) assertThat(count).isEqualTo(3L);
            if (videoId == 200L) assertThat(count).isEqualTo(1L);
            if (videoId == 300L) assertThat(count).isEqualTo(1L);
        }
    }

    @Test
    void findLikedVideoIdsByUserId_returnsOnlyMatchingIds() {
        List<Long> user1Liked = videoLikeRepository.findLikedVideoIdsByUserId(
                1L, List.of(100L, 200L, 300L, 999L));
        assertThat(user1Liked).containsExactlyInAnyOrder(100L, 200L);

        List<Long> user2Liked = videoLikeRepository.findLikedVideoIdsByUserId(
                2L, List.of(100L, 200L, 300L));
        assertThat(user2Liked).containsExactlyInAnyOrder(100L, 300L);
    }

    @Test
    void deleteByVideoId_removesAllLikesForVideo() {
        videoLikeRepository.deleteByVideoId(100L);
        videoLikeRepository.flush();

        assertThat(videoLikeRepository.countByVideoId(100L)).isZero();
        assertThat(videoLikeRepository.countByVideoId(200L)).isEqualTo(1L);
    }
}
