package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.S3StorageService;
import com.example.demo.entity.Video;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final AdminChecker adminChecker;
    private final S3StorageService storageService;

    public VideoUploadController(VideoRepository videoRepository, LoginUserResolver loginUserResolver,
                                 AdminChecker adminChecker, S3StorageService storageService) {
        this.videoRepository = videoRepository;
        this.loginUserResolver = loginUserResolver;
        this.adminChecker = adminChecker;
        this.storageService = storageService;
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

            if (title == null || title.trim().isEmpty()) return badRequest("제목을 입력해줘.");
            if (description == null || description.trim().isEmpty()) return badRequest("설명을 입력해줘.");
            if (channel == null || channel.trim().isEmpty()) return badRequest("채널명을 입력해줘.");
            if (duration == null || duration.trim().isEmpty()) return badRequest("영상 길이를 입력해줘.");

            // Step 1: save video file (local for FFmpeg, then S3)
            String videoUrl = "";
            String videoUuid = null;
            if (videoFile != null && !videoFile.isEmpty()) {
                videoUuid = saveVideoFileSync(videoFile, videoDir);
                if (storageService.isConfigured()) {
                    Path mp4Path = Paths.get(videoDir).toAbsolutePath().resolve(videoUuid + ".mp4");
                    videoUrl = storageService.upload(mp4Path, "videos/" + videoUuid + ".mp4", "video/mp4");
                } else {
                    videoUrl = "/uploads/videos/" + videoUuid + ".mp4";
                }
            } else if (embedUrl == null || embedUrl.trim().isEmpty()) {
                return badRequest("영상 파일을 선택하거나 YouTube URL을 입력해줘.");
            }

            // Step 2: save thumbnail
            String finalThumbnailUrl;
            if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
                if (storageService.isConfigured()) {
                    finalThumbnailUrl = uploadThumbnailToS3(thumbnailFile);
                } else {
                    String savedThumbnailFileName = saveFile(thumbnailFile, thumbnailDir);
                    finalThumbnailUrl = "/uploads/thumbnails/" + savedThumbnailFileName;
                }
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

            // Step 3: save entity to get ID
            Video savedVideo = videoRepository.save(video);

            // Step 4: trigger background H.264 conversion + resolution variants
            if (videoUuid != null) {
                final String uuid = videoUuid;
                final Long savedId = savedVideo.getId();
                final String ffmpeg = ffmpegPath;
                final Path dirPath = Paths.get(videoDir).toAbsolutePath();
                CompletableFuture.runAsync(() -> convertAndGenerateVariants(ffmpeg, uuid, dirPath, savedId));
            }

            UploadResponse response = new UploadResponse(
                    true, "업로드 완료",
                    savedVideo.getVideoUrl(), savedVideo.getThumbnail(),
                    savedVideo.getTitle(), savedVideo.getDescription(),
                    savedVideo.getChannel(), savedVideo.getDuration(),
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

    @PostMapping("/admin/generate-resolutions")
    public ResponseEntity<?> generateResolutionsForAll(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "관리자 권한이 필요합니다."));
        }

        List<Video> targets = videoRepository.findAll().stream()
                .filter(v -> v.getVideoUrl() != null && !v.getVideoUrl().isBlank())
                .filter(v -> isNullOrEmpty(v.getVideoUrl1080()) || isNullOrEmpty(v.getVideoUrl720())
                          || isNullOrEmpty(v.getVideoUrl480()) || isNullOrEmpty(v.getVideoUrl360()))
                .collect(Collectors.toList());

        final String ffmpeg = ffmpegPath;
        final Path dirPath = Paths.get(videoDir).toAbsolutePath();

        for (Video video : targets) {
            String fileName = video.getVideoUrl().contains("/videos/")
                    ? video.getVideoUrl().substring(video.getVideoUrl().lastIndexOf("/videos/") + 8)
                    : null;
            if (fileName == null) continue;
            String uuid = fileName.endsWith(".mp4") ? fileName.substring(0, fileName.length() - 4) : fileName;
            final Long videoId = video.getId();
            final Path sourcePath = dirPath.resolve(uuid + ".mp4");
            CompletableFuture.runAsync(() ->
                generateResolutionVariants(ffmpeg, sourcePath, dirPath, uuid, videoId)
            );
        }

        return ResponseEntity.ok(Map.of("success", true, "queued", targets.size(),
                "message", targets.size() + "개 영상의 해상도 변환을 백그라운드에서 시작했습니다."));
    }

    private String uploadThumbnailToS3(MultipartFile file) throws Exception {
        String ext = extensionOf(file.getOriginalFilename());
        String uuid = UUID.randomUUID().toString();
        String key = "thumbnails/" + uuid + ext;
        Path temp = Files.createTempFile("thumb_" + uuid, ext);
        try {
            file.transferTo(temp);
            return storageService.upload(temp, key, contentTypeFor(ext));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void convertAndGenerateVariants(String ffmpeg, String uuid, Path dirPath, Long videoId) {
        Path origPath = findOrigFile(dirPath, uuid);
        Path servePath = dirPath.resolve(uuid + ".mp4");
        Path tmpPath = dirPath.resolve(uuid + "_conv.mp4");

        if (origPath != null) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        ffmpeg, "-y",
                        "-i", origPath.toString(),
                        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                        "-c:a", "aac", "-b:a", "128k",
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
        }

        // Generate resolution variants (uses local servePath as source)
        generateResolutionVariants(ffmpeg, servePath, dirPath, uuid, videoId);

        // Upload converted main file to S3 (overwrite initial upload), then clean up local
        if (storageService.isConfigured() && servePath.toFile().exists()) {
            try {
                storageService.upload(servePath, "videos/" + uuid + ".mp4", "video/mp4");
                Files.deleteIfExists(servePath);
            } catch (Exception ignored) {}
        }
    }

    private Path findOrigFile(Path dirPath, String uuid) {
        String[] exts = {".mp4", ".mov", ".avi", ".mkv", ".webm", ".wmv", ".flv"};
        for (String ext : exts) {
            Path p = dirPath.resolve(uuid + "_orig" + ext);
            if (p.toFile().exists()) return p;
        }
        return null;
    }

    private void generateResolutionVariants(String ffmpeg, Path sourcePath, Path dirPath, String uuid, Long videoId) {
        if (!sourcePath.toFile().exists()) return;

        int[] heights = {1080, 720, 480, 360};
        String[] resultUrls = new String[heights.length];

        for (int i = 0; i < heights.length; i++) {
            int h = heights[i];
            Path variantPath = dirPath.resolve(uuid + "_" + h + "p.mp4");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        ffmpeg, "-y",
                        "-i", sourcePath.toString(),
                        "-vf", "scale=-2:'min(" + h + ",ih)'",
                        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                        "-c:a", "aac", "-b:a", "96k",
                        "-movflags", "+faststart",
                        variantPath.toString()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                process.getInputStream().transferTo(OutputStream.nullOutputStream());
                boolean done = process.waitFor(30, TimeUnit.MINUTES);
                if (done && process.exitValue() == 0) {
                    if (storageService.isConfigured()) {
                        resultUrls[i] = storageService.upload(variantPath, "videos/" + uuid + "_" + h + "p.mp4", "video/mp4");
                        Files.deleteIfExists(variantPath);
                    } else {
                        resultUrls[i] = "/uploads/videos/" + uuid + "_" + h + "p.mp4";
                    }
                }
            } catch (Exception ignored) {}
        }

        videoRepository.findById(videoId).ifPresent(video -> {
            if (resultUrls[0] != null) video.setVideoUrl1080(resultUrls[0]);
            if (resultUrls[1] != null) video.setVideoUrl720(resultUrls[1]);
            if (resultUrls[2] != null) video.setVideoUrl480(resultUrls[2]);
            if (resultUrls[3] != null) video.setVideoUrl360(resultUrls[3]);
            videoRepository.save(video);
        });
    }

    private String saveVideoFileSync(MultipartFile file, String dir) throws Exception {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = "";
        int dot = originalFilename.lastIndexOf(".");
        if (dot != -1) ext = originalFilename.substring(dot);

        String uuid = UUID.randomUUID().toString();
        Path origPath = Paths.get(dir).toAbsolutePath().resolve(uuid + "_orig" + ext);
        Path servePath = Paths.get(dir).toAbsolutePath().resolve(uuid + ".mp4");

        file.transferTo(origPath);
        Files.copy(origPath, servePath);

        return uuid;
    }

    private String saveFile(MultipartFile file, String dir) throws Exception {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf(".");
        if (lastDotIndex != -1) extension = originalFilename.substring(lastDotIndex);
        String savedFileName = UUID.randomUUID() + extension;
        Path savePath = Paths.get(dir).toAbsolutePath().resolve(savedFileName);
        file.transferTo(savePath);
        return savedFileName;
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    private String contentTypeFor(String ext) {
        return switch (ext.toLowerCase()) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.isBlank();
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
