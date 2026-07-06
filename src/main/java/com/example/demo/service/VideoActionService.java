package com.example.demo.service;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.VideoActionController.DislikeResponse;
import com.example.demo.controller.VideoActionController.LikeResponse;
import com.example.demo.controller.VideoActionController.SaveResponse;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoDislike;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoSave;
import com.example.demo.repository.VideoDislikeRepository;
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
    private final VideoDislikeRepository videoDislikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final NotificationService notificationService;

    public VideoActionService(
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoDislikeRepository videoDislikeRepository,
            VideoSaveRepository videoSaveRepository,
            NotificationService notificationService
    ) {
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoDislikeRepository = videoDislikeRepository;
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
            // 좋아요와 싫어요는 상호배타 — 좋아요를 누르면 기존 싫어요는 해제한다
            videoDislikeRepository.findByVideoIdAndUserId(id, loginUserId)
                    .ifPresent(videoDislikeRepository::delete);
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
        long dislikeCount = videoDislikeRepository.countByVideoId(id);
        boolean disliked = videoDislikeRepository.existsByVideoIdAndUserId(id, loginUserId);
        return new LikeResponse(true, liked, likeCount, disliked, dislikeCount);
    }

    public DislikeResponse toggleDislike(Long id, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long loginUserId = sessionUser.getId();

        videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        Optional<VideoDislike> existing = videoDislikeRepository.findByVideoIdAndUserId(id, loginUserId);

        boolean disliked;
        if (existing.isPresent()) {
            videoDislikeRepository.delete(existing.get());
            disliked = false;
        } else {
            // 상호배타 — 싫어요를 누르면 기존 좋아요는 해제한다
            videoLikeRepository.findByVideoIdAndUserId(id, loginUserId)
                    .ifPresent(videoLikeRepository::delete);
            VideoDislike videoDislike = new VideoDislike();
            videoDislike.setVideoId(id);
            videoDislike.setUserId(loginUserId);
            videoDislikeRepository.save(videoDislike);
            disliked = true;
        }

        long dislikeCount = videoDislikeRepository.countByVideoId(id);
        long likeCount = videoLikeRepository.countByVideoId(id);
        boolean liked = videoLikeRepository.existsByVideoIdAndUserId(id, loginUserId);
        return new DislikeResponse(true, disliked, dislikeCount, liked, likeCount);
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
