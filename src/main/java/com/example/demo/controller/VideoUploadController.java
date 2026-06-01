package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.S3StorageService;
import com.example.demo.entity.Notification;
import com.example.demo.entity.Subscription;
import com.example.demo.entity.Video;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.SubscriptionRepository;
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
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
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
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;

    // 동시에 처리할 최대 영상 수 (CPU 과부하 방지)
    private static final Semaphore BATCH_SEMAPHORE = new Semaphore(2);

    public VideoUploadController(VideoRepository videoRepository, LoginUserResolver loginUserResolver,
                                 AdminChecker adminChecker, S3StorageService storageService,
                                 SubscriptionRepository subscriptionRepository,
                                 NotificationRepository notificationRepository) {
        this.videoRepository = videoRepository;
        this.loginUserResolver = loginUserResolver;
        this.adminChecker = adminChecker;
        this.storageService = storageService;
        this.subscriptionRepository = subscriptionRepository;
        this.notificationRepository = notificationRepository;
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

            // Step 3: save entity to get ID
            Video savedVideo = videoRepository.save(video);

            // notify subscribers
            if ("공개".equals(savedVideo.getVisibility())) {
                List<Subscription> subs = subscriptionRepository.findByChannelOwnerId(sessionUser.getId());
                for (Subscription sub : subs) {
                    Notification notif = new Notification();
                    notif.setReceiverId(sub.getSubscriberId());
                    notif.setMessage(channel.trim() + "이(가) 새 영상을 올렸어요: " + title.trim());
                    notif.setRelatedVideoId(savedVideo.getId());
                    notif.setThumbnail(finalThumbnailUrl);
                    notificationRepository.save(notif);
                }
            }

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

            final String sourceInput = video.getVideoUrl().startsWith("http")
                    ? video.getVideoUrl()
                    : dirPath.resolve(uuid + ".mp4").toString();

            CompletableFuture.runAsync(() -> {
                try {
                    BATCH_SEMAPHORE.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    generateResolutionVariants(ffmpeg, sourceInput, dirPath, uuid, videoId);
                } finally {
                    BATCH_SEMAPHORE.release();
                }
            });
        }

        return ResponseEntity.ok(Map.of("success", true, "queued", targets.size(),
                "message", targets.size() + "개 영상의 해상도 변환을 백그라운드에서 시작했습니다."));
    }

    @GetMapping("/admin/generate-resolutions/status")
    public ResponseEntity<?> generateResolutionsStatus(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false));
        }
        List<Video> all = videoRepository.findAll().stream()
                .filter(v -> v.getVideoUrl() != null && !v.getVideoUrl().isBlank())
                .collect(java.util.stream.Collectors.toList());
        long done = all.stream()
                .filter(v -> !isNullOrEmpty(v.getVideoUrl1080()) && !isNullOrEmpty(v.getVideoUrl720())
                          && !isNullOrEmpty(v.getVideoUrl480()) && !isNullOrEmpty(v.getVideoUrl360()))
                .count();
        long total = all.size();
        long pending = total - done;
        return ResponseEntity.ok(Map.of("total", total, "done", done, "pending", pending));
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
        generateResolutionVariants(ffmpeg, servePath.toString(), dirPath, uuid, videoId);

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

    private int getSourceHeight(String ffmpeg, String sourceInput) {
        try {
            String ffprobe = ffmpeg.endsWith("ffmpeg") ? ffmpeg.replace("ffmpeg", "ffprobe")
                           : ffmpeg.endsWith("ffmpeg.exe") ? ffmpeg.replace("ffmpeg.exe", "ffprobe.exe")
                           : "ffprobe";
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobe, "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=height",
                    "-of", "csv=p=0",
                    sourceInput
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(30, TimeUnit.SECONDS);
            String firstLine = out.split("\n")[0].trim();
            return firstLine.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(firstLine);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private void generateResolutionVariants(String ffmpeg, String sourceInput, Path dirPath, String uuid, Long videoId) {
        // 로컬 파일이면 존재 여부 확인
        if (!sourceInput.startsWith("http") && !new java.io.File(sourceInput).exists()) return;

        // R2 URL이면 로컬에 미리 다운받아서 인코딩 속도 향상
        Path downloadedTemp = null;
        if (sourceInput.startsWith("http")) {
            downloadedTemp = dirPath.resolve(uuid + "_dl_tmp.mp4");
            try {
                System.out.println("[Batch] R2 다운로드 시작: " + uuid);
                try (var in = new java.net.URI(sourceInput).toURL().openStream()) {
                    Files.copy(in, downloadedTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("[Batch] R2 다운로드 완료: " + uuid);
                sourceInput = downloadedTemp.toString();
            } catch (Exception e) {
                System.err.println("[Batch] R2 다운로드 실패 [" + uuid + "]: " + e.getMessage());
                try { Files.deleteIfExists(downloadedTemp); } catch (Exception ignored) {}
                downloadedTemp = null;
                // 실패 시 원래 URL로 계속 진행
            }
        }

        int sourceHeight = getSourceHeight(ffmpeg, sourceInput);
        // 오래 걸리는 순서(고해상도 → 저해상도)로 순차 처리, 완료 즉시 DB 저장
        int[] allHeights  = {1080, 720, 480, 360};
        int[] orderedHeights = {1080, 720, 480, 360};

        for (int h : orderedHeights) {
            if (h > sourceHeight) continue;

            Path variantPath = dirPath.resolve(uuid + "_" + h + "p.mp4");
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpeg); cmd.add("-y");
            cmd.add("-i"); cmd.add(sourceInput);
            cmd.add("-vf"); cmd.add("scale=-2:" + h);
            cmd.add("-c:v"); cmd.add("libx264");
            cmd.add("-preset"); cmd.add("ultrafast");
            cmd.add("-crf"); cmd.add("26");
            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-b:a"); cmd.add("96k");
            cmd.add("-movflags"); cmd.add("+faststart");
            cmd.add(variantPath.toString());

            try {
                System.out.println("[Batch] " + h + "p 시작 [" + uuid + "]");
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                process.getInputStream().transferTo(OutputStream.nullOutputStream());
                boolean done = process.waitFor(60, TimeUnit.MINUTES);
                int exitCode = done ? process.exitValue() : -1;
                System.out.println("[Batch] " + h + "p 종료코드=" + exitCode + " [" + uuid + "]");

                if (done && exitCode == 0) {
                    String url;
                    if (storageService.isConfigured()) {
                        try {
                            url = storageService.upload(variantPath, "videos/" + uuid + "_" + h + "p.mp4", "video/mp4");
                            Files.deleteIfExists(variantPath);
                        } catch (Exception e) {
                            System.err.println("[Batch] R2 업로드 실패 " + h + "p [" + uuid + "]: " + e.getMessage());
                            continue;
                        }
                    } else {
                        url = "/uploads/videos/" + uuid + "_" + h + "p.mp4";
                    }
                    // 완료 즉시 DB 저장 → 플레이어에서 바로 사용 가능
                    final String finalUrl = url;
                    final int finalH = h;
                    videoRepository.findById(videoId).ifPresent(video -> {
                        if (finalH == 1080) video.setVideoUrl1080(finalUrl);
                        else if (finalH == 720) video.setVideoUrl720(finalUrl);
                        else if (finalH == 480) video.setVideoUrl480(finalUrl);
                        else if (finalH == 360) video.setVideoUrl360(finalUrl);
                        videoRepository.save(video);
                    });
                    System.out.println("[Batch] " + h + "p DB 저장 완료 [" + uuid + "]");
                }
            } catch (Exception e) {
                System.err.println("[Batch] " + h + "p 오류 [" + uuid + "]: " + e.getMessage());
            }
        }

        // 임시 다운로드 파일 정리
        if (downloadedTemp != null) {
            try { Files.deleteIfExists(downloadedTemp); } catch (Exception ignored) {}
        }
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
