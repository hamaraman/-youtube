package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoHistoryRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    @Value("${admin.password:admin1234}")
    private String adminPassword;

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final CommentRepository commentRepository;
    private final VideoHistoryRepository videoHistoryRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(VideoRepository videoRepository, UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           VideoLikeRepository videoLikeRepository,
                           VideoSaveRepository videoSaveRepository,
                           CommentRepository commentRepository,
                           VideoHistoryRepository videoHistoryRepository,
                           JdbcTemplate jdbcTemplate) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.commentRepository = commentRepository;
        this.videoHistoryRepository = videoHistoryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
            "ALTER TABLE videos ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0"
        );

        // 관리자 계정 생성 / role 업데이트
        userRepository.findByUsername("admin").ifPresentOrElse(
            admin -> {
                admin.setRole("ADMIN");
                admin.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(admin);
            },
            () -> {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode(adminPassword));
                admin.setNickname("관리자");
                admin.setChannelName("관리자");
                admin.setEmail(null);
                admin.setRole("ADMIN");
                userRepository.save(admin);
            }
        );

        // 더미 데이터(ownerId가 null인 영상) 일괄 삭제
        var dummies = videoRepository.findByOwnerIdIsNull();
        if (!dummies.isEmpty()) {
            dummies.forEach(v -> deleteVideoAndRelated(v.getId()));
        }

        // 특정 제목 영상 삭제
        videoRepository.findByTitleContaining("첫 영상 제목입니다")
                .forEach(v -> deleteVideoAndRelated(v.getId()));
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
