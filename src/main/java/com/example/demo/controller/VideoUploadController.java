package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.Video;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class VideoUploadController {

    @Value("${file.video-dir}")
    private String videoDir;

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    private final VideoRepository videoRepository;
    private final LoginUserResolver loginUserResolver;

    public VideoUploadController(VideoRepository videoRepository, LoginUserResolver loginUserResolver) {
        this.videoRepository = videoRepository;
        this.loginUserResolver = loginUserResolver;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("channel") String channel,
            @RequestParam("duration") String duration,
            @RequestParam(value = "visibility", defaultValue = "공개") String visibility,
            @RequestParam(value = "avatar", required = false) String avatar,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "embedUrl", required = false) String embedUrl,
            @RequestParam(value = "thumbnailUrl", required = false) String thumbnailUrl,
            @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
            HttpSession session
    ) {
        try {
            AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
            if (sessionUser == null) {
                return ResponseEntity.status(401).body(basicFail("로그인이 필요합니다."));
            }

            if (title == null || title.trim().isEmpty()) {
                return badRequest("제목을 입력해줘.");
            }

            if (description == null || description.trim().isEmpty()) {
                return badRequest("설명을 입력해줘.");
            }

            if (channel == null || channel.trim().isEmpty()) {
                return badRequest("채널명을 입력해줘.");
            }

            if (duration == null || duration.trim().isEmpty()) {
                return badRequest("영상 길이를 입력해줘.");
            }

            String videoUrl = "";
            if (videoFile != null && !videoFile.isEmpty()) {
                String savedVideoFileName = saveAndConvertVideo(videoFile, videoDir);
                videoUrl = "/uploads/videos/" + savedVideoFileName;
            } else if (embedUrl == null || embedUrl.trim().isEmpty()) {
                return badRequest("영상 파일을 선택하거나 YouTube URL을 입력해줘.");
            }

            String finalThumbnailUrl;

            if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
                String savedThumbnailFileName = saveFile(thumbnailFile, thumbnailDir);
                finalThumbnailUrl = "/uploads/thumbnails/" + savedThumbnailFileName;
            } else if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) {
                finalThumbnailUrl = thumbnailUrl.trim();
            } else {
                return badRequest("썸네일 파일 또는 썸네일 URL이 필요해.");
            }

            Video video = new Video();
            video.setOwnerId(sessionUser.getId());
            video.setTitle(title.trim());
            video.setDescription(description.trim());
            video.setChannel(channel.trim());
            video.setAvatar((avatar == null || avatar.trim().isEmpty()) ? "" : avatar.trim());
            video.setCategory((category == null || category.trim().isEmpty()) ? "기타" : category.trim());
            video.setDuration(duration.trim());
            video.setVisibility((visibility == null || visibility.trim().isEmpty()) ? "공개" : visibility.trim());
            video.setEmbedUrl((embedUrl == null || embedUrl.trim().isEmpty()) ? "" : embedUrl.trim());
            video.setThumbnail(finalThumbnailUrl);
            video.setVideoUrl(videoUrl);
            video.setDateText("방금 전");

            Video savedVideo = videoRepository.save(video);

            UploadResponse response = new UploadResponse(
                    true,
                    "업로드 완료",
                    savedVideo.getVideoUrl(),
                    savedVideo.getThumbnail(),
                    savedVideo.getTitle(),
                    savedVideo.getDescription(),
                    savedVideo.getChannel(),
                    savedVideo.getDuration(),
                    savedVideo.getVisibility()
            );

            response.setId(savedVideo.getId());
            response.setCategory(savedVideo.getCategory());
            response.setAvatar(savedVideo.getAvatar());
            response.setEmbedUrl(savedVideo.getEmbedUrl());
            response.setDate(savedVideo.getDateText());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();

            UploadResponse response = new UploadResponse();
            response.setSuccess(false);
            response.setMessage("업로드 중 오류가 발생했습니다.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private String saveAndConvertVideo(MultipartFile file, String dir) throws Exception {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = "";
        int dot = originalFilename.lastIndexOf(".");
        if (dot != -1) ext = originalFilename.substring(dot);

        String uuid = UUID.randomUUID().toString();
        Path origPath = Paths.get(dir).toAbsolutePath().resolve(uuid + "_orig" + ext);
        Path servePath = Paths.get(dir).toAbsolutePath().resolve(uuid + ".mp4");

        // Save original immediately so upload response can return
        file.transferTo(origPath);
        Files.copy(origPath, servePath);

        // Convert to H.264 in background; replace servePath when done
        String ffmpeg = ffmpegPath;
        CompletableFuture.runAsync(() -> {
            Path tmpPath = Paths.get(dir).toAbsolutePath().resolve(uuid + "_conv.mp4");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        ffmpeg, "-y",
                        "-i", origPath.toString(),
                        "-c:v", "libx264",
                        "-preset", "fast",
                        "-crf", "23",
                        "-c:a", "aac",
                        "-b:a", "128k",
                        "-movflags", "+faststart",
                        tmpPath.toString()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();

                Thread drain = new Thread(() -> {
                    try { process.getInputStream().transferTo(OutputStream.nullOutputStream()); }
                    catch (Exception ignored) {}
                });
                drain.setDaemon(true);
                drain.start();

                boolean done = process.waitFor(60, TimeUnit.MINUTES);
                if (done && process.exitValue() == 0) {
                    Files.move(tmpPath, servePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(origPath);
                } else {
                    Files.deleteIfExists(tmpPath);
                }
            } catch (Exception e) {
                try { Files.deleteIfExists(tmpPath); } catch (Exception ignored) {}
            }
        });

        return uuid + ".mp4";
    }

    private String saveFile(MultipartFile file, String dir) throws Exception {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf(".");
        if (lastDotIndex != -1) {
            extension = originalFilename.substring(lastDotIndex);
        }

        String savedFileName = UUID.randomUUID() + extension;
        Path savePath = Paths.get(dir).toAbsolutePath().resolve(savedFileName);

        file.transferTo(savePath);

        return savedFileName;
    }

    private ResponseEntity<UploadResponse> badRequest(String message) {
        UploadResponse response = new UploadResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return ResponseEntity.badRequest().body(response);
    }

    private UploadResponse basicFail(String message) {
        UploadResponse response = new UploadResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}