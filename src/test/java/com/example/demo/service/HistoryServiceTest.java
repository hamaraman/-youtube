package com.example.demo.service;

import com.example.demo.entity.Video;
import com.example.demo.entity.VideoHistory;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.VideoHistoryRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock private VideoHistoryRepository videoHistoryRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private VideoLikeRepository videoLikeRepository;
    @Mock private VideoSaveRepository videoSaveRepository;
    @Mock private CommentRepository commentRepository;

    @InjectMocks private HistoryService historyService;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Video video(Long id, Long ownerId, long views, String visibility) {
        Video v = new Video();
        v.setId(id); v.setOwnerId(ownerId); v.setViewCount(views);
        v.setTitle("t"); v.setChannel("ch"); v.setThumbnail("th"); v.setDuration("1:00");
        v.setVisibility(visibility);
        return v;
    }

    @Nested
    class MarkHistory {

        @Test
        void videoMissing_throwsNotFound() {
            when(videoRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> historyService.markHistory(1L, 10L, new HashMap<>()))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void anonNewViewer_incrementsCountAndRecordsTimestamp() {
            Video v = video(1L, 5L, 100L, "공개");
            when(videoRepository.findById(1L)).thenReturn(Optional.of(v));
            Map<Long, Long> viewedAt = new HashMap<>();

            historyService.markHistory(1L, null, viewedAt);

            assertThat(v.getViewCount()).isEqualTo(101L);
            verify(videoRepository).save(v);
            assertThat(viewedAt).containsKey(1L);
        }

        @Test
        void anonRecentViewer_doesNotIncrement() {
            Video v = video(1L, 5L, 100L, "공개");
            when(videoRepository.findById(1L)).thenReturn(Optional.of(v));
            Map<Long, Long> viewedAt = new HashMap<>();
            viewedAt.put(1L, System.currentTimeMillis() - 60_000L);

            historyService.markHistory(1L, null, viewedAt);

            assertThat(v.getViewCount()).isEqualTo(100L);
            verify(videoRepository, never()).save(any());
        }

        @Test
        void anonExpiredCooldown_incrementsAgain() {
            Video v = video(1L, 5L, 100L, "공개");
            when(videoRepository.findById(1L)).thenReturn(Optional.of(v));
            Map<Long, Long> viewedAt = new HashMap<>();
            viewedAt.put(1L, System.currentTimeMillis() - DAY_MS - 60_000L);

            historyService.markHistory(1L, null, viewedAt);

            assertThat(v.getViewCount()).isEqualTo(101L);
            verify(videoRepository).save(v);
        }

        @Test
        void loggedInFirstTime_createsHistoryAndIncrementsCount() {
            Video v = video(1L, 5L, 100L, "공개");
            when(videoRepository.findById(1L)).thenReturn(Optional.of(v));
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

            historyService.markHistory(1L, 10L, new HashMap<>());

            ArgumentCaptor<VideoHistory> captor = ArgumentCaptor.forClass(VideoHistory.class);
            verify(videoHistoryRepository).save(captor.capture());
            VideoHistory saved = captor.getValue();
            assertThat(saved.getVideoId()).isEqualTo(1L);
            assertThat(saved.getUserId()).isEqualTo(10L);
            assertThat(saved.getWatchedAt()).isNotNull();
            assertThat(v.getViewCount()).isEqualTo(101L);
            verify(videoRepository).save(v);
        }

        @Test
        void loggedInWithinCooldown_updatesTimestampButNotCount() {
            Video v = video(1L, 5L, 100L, "공개");
            VideoHistory existing = new VideoHistory();
            existing.setId(99L); existing.setVideoId(1L); existing.setUserId(10L);
            existing.setWatchedAt(System.currentTimeMillis() - 60_000L);

            when(videoRepository.findById(1L)).thenReturn(Optional.of(v));
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));

            historyService.markHistory(1L, 10L, new HashMap<>());

            verify(videoHistoryRepository).save(existing);
            verify(videoRepository, never()).save(any());
            assertThat(v.getViewCount()).isEqualTo(100L);
        }

        @Test
        void loggedInAfterCooldown_incrementsCount() {
            Video v = video(1L, 5L, 100L, "공개");
            VideoHistory existing = new VideoHistory();
            existing.setVideoId(1L); existing.setUserId(10L);
            existing.setWatchedAt(System.currentTimeMillis() - DAY_MS - 60_000L);

            when(videoRepository.findById(1L)).thenReturn(Optional.of(v));
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));

            historyService.markHistory(1L, 10L, new HashMap<>());

            verify(videoRepository).save(v);
            assertThat(v.getViewCount()).isEqualTo(101L);
        }
    }

    @Nested
    class SaveProgress {

        @Test
        void negativePosition_throwsBadRequest() {
            assertThatThrownBy(() -> historyService.saveProgress(1L, -1.0, 10L))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void nullPosition_throwsBadRequest() {
            assertThatThrownBy(() -> historyService.saveProgress(1L, null, 10L))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void firstSave_createsHistory() {
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

            historyService.saveProgress(1L, 42.5, 10L);

            ArgumentCaptor<VideoHistory> captor = ArgumentCaptor.forClass(VideoHistory.class);
            verify(videoHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getLastPosition()).isEqualTo(42.5);
        }

        @Test
        void existingHistory_updatesPosition() {
            VideoHistory existing = new VideoHistory();
            existing.setVideoId(1L); existing.setUserId(10L); existing.setLastPosition(10.0);
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));

            historyService.saveProgress(1L, 88.0, 10L);

            assertThat(existing.getLastPosition()).isEqualTo(88.0);
            verify(videoHistoryRepository).save(existing);
        }
    }

    @Nested
    class GetProgress {

        @Test
        void anonymous_returnsZero() {
            assertThat(historyService.getProgress(1L, null)).isEqualTo(0.0);
            verifyNoInteractions(videoHistoryRepository);
        }

        @Test
        void noHistory_returnsZero() {
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
            assertThat(historyService.getProgress(1L, 10L)).isEqualTo(0.0);
        }

        @Test
        void nullPosition_returnsZero() {
            VideoHistory h = new VideoHistory();
            h.setLastPosition(null);
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(h));
            assertThat(historyService.getProgress(1L, 10L)).isEqualTo(0.0);
        }

        @Test
        void withPosition_returnsValue() {
            VideoHistory h = new VideoHistory();
            h.setLastPosition(42.5);
            when(videoHistoryRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(h));
            assertThat(historyService.getProgress(1L, 10L)).isEqualTo(42.5);
        }
    }

    @Nested
    class GetMyProgress {

        @Test
        void anonymous_returnsEmpty() {
            assertThat(historyService.getMyProgress(null)).isEmpty();
            verifyNoInteractions(videoHistoryRepository);
        }

        @Test
        void filtersOutZeroAndNullPositions() {
            VideoHistory h1 = new VideoHistory(); h1.setVideoId(1L); h1.setLastPosition(50.0);
            VideoHistory h2 = new VideoHistory(); h2.setVideoId(2L); h2.setLastPosition(0.0);
            VideoHistory h3 = new VideoHistory(); h3.setVideoId(3L); h3.setLastPosition(null);
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(10L))
                    .thenReturn(List.of(h1, h2, h3));

            Map<Long, Double> result = historyService.getMyProgress(10L);

            assertThat(result).containsOnlyKeys(1L);
            assertThat(result).containsEntry(1L, 50.0);
        }
    }

    @Nested
    class GetMyHistory {

        @Test
        void noHistory_returnsEmpty() {
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(10L)).thenReturn(List.of());
            assertThat(historyService.getMyHistory(10L)).isEmpty();
        }

        @Test
        void filtersOutPrivateOthersVideos() {
            VideoHistory h1 = new VideoHistory(); h1.setVideoId(1L);
            VideoHistory h2 = new VideoHistory(); h2.setVideoId(2L);
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(10L)).thenReturn(List.of(h1, h2));
            when(videoRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                    video(1L, 5L, 0L, "공개"),
                    video(2L, 5L, 0L, "비공개")));
            when(videoLikeRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(commentRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(10L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(10L), anyList())).thenReturn(List.of());

            var result = historyService.getMyHistory(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
        }

        @Test
        void keepsPrivateVideoIfIAmOwner() {
            VideoHistory h = new VideoHistory(); h.setVideoId(2L);
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(5L)).thenReturn(List.of(h));
            when(videoRepository.findAllById(List.of(2L)))
                    .thenReturn(List.of(video(2L, 5L, 0L, "비공개")));
            when(videoLikeRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(commentRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(5L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(5L), anyList())).thenReturn(List.of());

            var result = historyService.getMyHistory(5L);

            assertThat(result).hasSize(1);
        }
    }
}
