package com.example.demo.service;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.VideoActionController.DislikeResponse;
import com.example.demo.controller.VideoActionController.LikeResponse;
import com.example.demo.controller.VideoActionController.ReportResponse;
import com.example.demo.controller.VideoActionController.SaveResponse;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoDislike;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoReport;
import com.example.demo.entity.VideoSave;
import com.example.demo.repository.VideoDislikeRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoReportRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoActionServiceTest {

    @Mock private VideoRepository videoRepository;
    @Mock private VideoLikeRepository videoLikeRepository;
    @Mock private VideoDislikeRepository videoDislikeRepository;
    @Mock private VideoSaveRepository videoSaveRepository;
    @Mock private VideoReportRepository videoReportRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private VideoActionService videoActionService;

    private SessionUser sessionUser;
    private Video video;

    @BeforeEach
    void setUp() {
        sessionUser = new SessionUser(10L, "user", "닉네임", "u@e.com", "채널명", "img", "USER");
        video = new Video();
        video.setId(1L);
        video.setOwnerId(99L);
        video.setTitle("hello");
        video.setThumbnail("thumb.png");
    }

    @Test
    void toggleLike_withoutLogin_throwsUnauthorized() {
        assertThatThrownBy(() -> videoActionService.toggleLike(1L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("로그인");
    }

    @Test
    void toggleLike_videoMissing_throwsNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> videoActionService.toggleLike(1L, sessionUser))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("영상");
    }

    @Test
    void toggleLike_whenNoExistingLike_createsLikeAndNotifiesOwner() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoLikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(videoLikeRepository.countByVideoId(1L)).thenReturn(7L);

        LikeResponse response = videoActionService.toggleLike(1L, sessionUser);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeCount()).isEqualTo(7L);
        verify(videoLikeRepository).save(any(VideoLike.class));
        verify(videoLikeRepository, never()).delete(any());
        verify(notificationService).send(eq(99L), eq(10L), eq("LIKE"), anyString(), eq(1L), eq("thumb.png"));
    }

    @Test
    void toggleLike_whenExistingLike_deletesLikeAndDoesNotNotify() {
        VideoLike existing = new VideoLike();
        existing.setId(555L);
        existing.setVideoId(1L);
        existing.setUserId(10L);

        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoLikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));
        when(videoLikeRepository.countByVideoId(1L)).thenReturn(3L);

        LikeResponse response = videoActionService.toggleLike(1L, sessionUser);

        assertThat(response.isLiked()).isFalse();
        assertThat(response.getLikeCount()).isEqualTo(3L);
        verify(videoLikeRepository).delete(existing);
        verify(videoLikeRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void toggleLike_useNicknameWhenChannelNameBlank() {
        sessionUser = new SessionUser(10L, "user", "닉네임", "u@e.com", "  ", "img", "USER");
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoLikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(videoLikeRepository.countByVideoId(1L)).thenReturn(1L);

        videoActionService.toggleLike(1L, sessionUser);

        verify(notificationService).send(anyLong(), anyLong(), anyString(),
                org.mockito.ArgumentMatchers.argThat(msg -> msg != null && msg.contains("닉네임")),
                anyLong(), anyString());
    }

    @Test
    void toggleLike_whenNoExistingLike_removesExistingDislike() {
        VideoDislike existingDislike = new VideoDislike();
        existingDislike.setId(8L);
        existingDislike.setVideoId(1L);
        existingDislike.setUserId(10L);

        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoLikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(videoDislikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existingDislike));
        when(videoLikeRepository.countByVideoId(1L)).thenReturn(5L);
        when(videoDislikeRepository.countByVideoId(1L)).thenReturn(0L);

        LikeResponse response = videoActionService.toggleLike(1L, sessionUser);

        assertThat(response.isLiked()).isTrue();
        assertThat(response.isDisliked()).isFalse();
        assertThat(response.getDislikeCount()).isEqualTo(0L);
        verify(videoDislikeRepository).delete(existingDislike);
        verify(videoLikeRepository).save(any(VideoLike.class));
    }

    @Test
    void toggleDislike_withoutLogin_throwsUnauthorized() {
        assertThatThrownBy(() -> videoActionService.toggleDislike(1L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("로그인");
    }

    @Test
    void toggleDislike_videoMissing_throwsNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> videoActionService.toggleDislike(1L, sessionUser))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("영상");
    }

    @Test
    void toggleDislike_whenNoExistingDislike_createsDislikeRemovesLikeAndDoesNotNotify() {
        VideoLike existingLike = new VideoLike();
        existingLike.setId(9L);
        existingLike.setVideoId(1L);
        existingLike.setUserId(10L);

        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoDislikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(videoLikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existingLike));
        when(videoDislikeRepository.countByVideoId(1L)).thenReturn(4L);

        DislikeResponse response = videoActionService.toggleDislike(1L, sessionUser);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isDisliked()).isTrue();
        assertThat(response.getDislikeCount()).isEqualTo(4L);
        assertThat(response.isLiked()).isFalse();
        verify(videoLikeRepository).delete(existingLike);
        verify(videoDislikeRepository).save(any(VideoDislike.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void toggleDislike_whenExistingDislike_deletesDislike() {
        VideoDislike existing = new VideoDislike();
        existing.setId(3L);
        existing.setVideoId(1L);
        existing.setUserId(10L);

        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoDislikeRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));
        when(videoDislikeRepository.countByVideoId(1L)).thenReturn(2L);

        DislikeResponse response = videoActionService.toggleDislike(1L, sessionUser);

        assertThat(response.isDisliked()).isFalse();
        assertThat(response.getDislikeCount()).isEqualTo(2L);
        verify(videoDislikeRepository).delete(existing);
        verify(videoDislikeRepository, never()).save(any());
        verify(videoLikeRepository, never()).delete(any());
    }

    @Test
    void reportVideo_withoutLogin_throwsUnauthorized() {
        assertThatThrownBy(() -> videoActionService.reportVideo(1L, "스팸", "", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("로그인");
    }

    @Test
    void reportVideo_emptyReason_throwsBadRequest() {
        assertThatThrownBy(() -> videoActionService.reportVideo(1L, "  ", "detail", sessionUser))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("사유");
    }

    @Test
    void reportVideo_videoMissing_throwsNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> videoActionService.reportVideo(1L, "스팸", "", sessionUser))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("영상");
    }

    @Test
    void reportVideo_firstTime_savesReport() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoReportRepository.existsByVideoIdAndReporterId(1L, 10L)).thenReturn(false);

        ReportResponse response = videoActionService.reportVideo(1L, "저작권 침해", "내 영상이에요", sessionUser);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isAlreadyReported()).isFalse();
        verify(videoReportRepository).save(any(VideoReport.class));
    }

    @Test
    void reportVideo_duplicate_doesNotSaveAgain() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoReportRepository.existsByVideoIdAndReporterId(1L, 10L)).thenReturn(true);

        ReportResponse response = videoActionService.reportVideo(1L, "스팸", "", sessionUser);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isAlreadyReported()).isTrue();
        verify(videoReportRepository, never()).save(any());
    }

    @Test
    void toggleSave_withoutLogin_throwsUnauthorized() {
        assertThatThrownBy(() -> videoActionService.toggleSave(1L, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void toggleSave_whenNoExistingSave_savesVideo() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoSaveRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        SaveResponse response = videoActionService.toggleSave(1L, 10L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isSaved()).isTrue();
        verify(videoSaveRepository).save(any(VideoSave.class));
    }

    @Test
    void toggleSave_whenExistingSave_deletesSave() {
        VideoSave existing = new VideoSave();
        existing.setVideoId(1L);
        existing.setUserId(10L);

        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoSaveRepository.findByVideoIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));

        SaveResponse response = videoActionService.toggleSave(1L, 10L);

        assertThat(response.isSaved()).isFalse();
        verify(videoSaveRepository).delete(existing);
    }

}
