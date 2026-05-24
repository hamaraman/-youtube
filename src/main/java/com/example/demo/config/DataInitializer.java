package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoHistoryRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import jakarta.transaction.Transactional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final CommentRepository commentRepository;
    private final VideoHistoryRepository videoHistoryRepository;

    public DataInitializer(VideoRepository videoRepository, UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           VideoLikeRepository videoLikeRepository,
                           VideoSaveRepository videoSaveRepository,
                           CommentRepository commentRepository,
                           VideoHistoryRepository videoHistoryRepository) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.commentRepository = commentRepository;
        this.videoHistoryRepository = videoHistoryRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 관리자 계정 생성
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin1234"));
            admin.setNickname("관리자");
            admin.setChannelName("관리자");
            admin.setEmail(null);
            userRepository.save(admin);
        }

        // 더미 데이터(ownerId가 null인 영상) 일괄 삭제
        var dummies = videoRepository.findByOwnerIdIsNull();
        if (!dummies.isEmpty()) {
            dummies.forEach(v -> deleteVideoAndRelated(v.getId()));
        }
    }

    @Transactional
    public void deleteVideoAndRelated(Long videoId) {
        videoLikeRepository.deleteByVideoId(videoId);
        videoSaveRepository.deleteByVideoId(videoId);
        commentRepository.deleteByVideoId(videoId);
        videoHistoryRepository.deleteByVideoId(videoId);
        videoRepository.deleteById(videoId);
    }
}
