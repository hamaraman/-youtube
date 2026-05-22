package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class UploadDirectoryConfig implements ApplicationRunner {

    @Value("${file.video-dir}")
    private String videoDir;

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    @Override
    public void run(ApplicationArguments args) {
        new File(videoDir).mkdirs();
        new File(thumbnailDir).mkdirs();
    }
}
