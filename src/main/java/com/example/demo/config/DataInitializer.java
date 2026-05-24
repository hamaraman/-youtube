package com.example.demo.config;

import com.example.demo.repository.VideoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final VideoRepository videoRepository;

    public DataInitializer(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 더미 데이터(ownerId가 null인 영상) 일괄 삭제
        var dummies = videoRepository.findByOwnerIdIsNull();
        if (!dummies.isEmpty()) {
            videoRepository.deleteAll(dummies);
        }
    }
}
