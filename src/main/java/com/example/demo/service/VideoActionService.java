package com.example.demo.service;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.VideoActionController.LikeResponse;
import com.example.demo.controller.VideoActionController.SaveResponse;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoSave;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class VideoActionService {

    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final NotificationService notificationService;

    public VideoActionService(
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoSaveRepository videoSaveRepository,
            NotificationService notificationService
    ) {
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.notificationService = notificationService;
    }

    public LikeResponse toggleLike(Long id, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long loginUserId = sessionUser.getId();

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        Optional<VideoLike> existing = videoLikeRepository.findByVideoIdAndUserId(id, loginUserId);

        boolean liked;
        if (existing.isPresent()) {
            videoLikeRepository.delete(existing.get());
            liked = false;
        } else {
            VideoLike videoLike = new VideoLike();
            videoLike.setVideoId(id);
            videoLike.setUserId(loginUserId);
            videoLikeRepository.save(videoLike);
            liked = true;
            String name = sessionUser.getChannelName() != null && !sessionUser.getChannelName().isBlank()
                    ? sessionUser.getChannelName() : sessionUser.getNickname();
            notificationService.send(video.getOwnerId(), loginUserId, "LIKE",
                    name + "님이 좋아요를 눌렀어요: " + video.getTitle(),
                    id, video.getThumbnail());
        }

        long likeCount = videoLikeRepository.countByVideoId(id);
        return new LikeResponse(true, liked, likeCount);
    }

    public SaveResponse toggleSave(Long id, Long loginUserId) {
        if (loginUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        Optional<VideoSave> existing = videoSaveRepository.findByVideoIdAndUserId(id, loginUserId);

        boolean saved;
        if (existing.isPresent()) {
            videoSaveRepository.delete(existing.get());
            saved = false;
        } else {
            VideoSave videoSave = new VideoSave();
            videoSave.setVideoId(id);
            videoSave.setUserId(loginUserId);
            videoSaveRepository.save(videoSave);
            saved = true;
        }

        return new SaveResponse(true, saved);
    }
}
