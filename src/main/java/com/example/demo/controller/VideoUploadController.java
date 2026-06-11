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
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;
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

    @Value("${ffmpeg.hw-accel:none}")
    private String ffmpegHwAccel;

    private final VideoRepository videoRepository;
    private final LoginUserResolver loginUserResolver;
    private final AdminChecker adminChecker;
    private final S3StorageService storageService;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;

    private static final Semaphore BATCH_SEMAPHORE = new Semaphore(2);
    private static final java.util.concurrent.ConcurrentHashMap<Long, String> ENCODE_STATUS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, Long> ENCODE_STATUS_TIME =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final Set<String> ALLOWED_VIDEO_EXTS =
            Set.of(".mp4", ".mov", ".avi", ".mkv", ".webm", ".wmv", ".flv");
    private static final Set<String> ALLOWED_IMAGE_EXTS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final long MAX_VIDEO_BYTES  = 2_000L * 1024 * 1024; // 2 GB
    private static final long MAX_IMAGE_BYTES  =    20L * 1024 * 1024; // 20 MB

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
                String videoExt = extensionOf(videoFile.getOriginalFilename()).toLowerCase();
                if (!ALLOWED_VIDEO_EXTS.contains(videoExt))
                    return badRequest("허용되지 않는 영상 형식입니다. (mp4, mov, avi, mkv, webm, wmv, flv)");
                if (videoFile.getSize() > MAX_VIDEO_BYTES)
                    return badRequest("영상 파일 크기는 2GB를 초과할 수 없습니다.");
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

            // Step 2: save thumbnail (없으면 영상 파일로부터 자동 생성)
            String finalThumbnailUrl;
            if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
                String thumbExt = extensionOf(thumbnailFile.getOriginalFilename()).toLowerCase();
                if (!ALLOWED_IMAGE_EXTS.contains(thumbExt))
                    return badRequest("허용되지 않는 썸네일 형식입니다. (jpg, jpeg, png, gif, webp)");
                if (thumbnailFile.getSize() > MAX_IMAGE_BYTES)
                    return badRequest("썸네일 파일 크기는 20MB를 초과할 수 없습니다.");
                if (storageService.isConfigured()) {
                    finalThumbnailUrl = uploadThumbnailToS3(thumbnailFile);
                } else {
                    String savedThumbnailFileName = saveFile(thumbnailFile, thumbnailDir);
                    finalThumbnailUrl = "/uploads/thumbnails/" + savedThumbnailFileName;
                }
            } else if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) {
                finalThumbnailUrl = thumbnailUrl.trim();
            } else if (videoUuid != null) {
                finalThumbnailUrl = ""; // 자동 생성 예정
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
                    notif.setType("VIDEO");
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
                setEncodeStatus(savedId, "QUEUED");
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
            response.setMessage("업로드 중 오류가 발생했습니다: " + e.getClass().getSimpleName() + " - " + e.getMessage());
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

    @PostMapping("/admin/migrate-to-r2")
    public ResponseEntity<?> migrateToR2(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "관리자 권한이 필요합니다."));
        }
        if (!storageService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "R2가 설정되지 않았습니다."));
        }

        List<Video> targets = videoRepository.findAll().stream()
                .filter(v -> v.getVideoUrl() != null && v.getVideoUrl().startsWith("/uploads/"))
                .collect(Collectors.toList());

        final Path videoDirPath = Paths.get(videoDir).toAbsolutePath();
        final Path thumbDirPath = Paths.get(thumbnailDir).toAbsolutePath();

        for (Video video : targets) {
            final Long videoId = video.getId();
            CompletableFuture.runAsync(() -> {
                try {
                    BATCH_SEMAPHORE.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    migrateVideoFiles(videoId, videoDirPath, thumbDirPath);
                } finally {
                    BATCH_SEMAPHORE.release();
                }
            });
        }

        return ResponseEntity.ok(Map.of("success", true, "queued", targets.size(),
                "message", targets.size() + "개 영상의 R2 마이그레이션을 백그라운드에서 시작했습니다."));
    }

    @GetMapping("/videos/{id}/encode-status")
    public ResponseEntity<?> encodeStatus(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        Optional<Video> opt = videoRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getOwnerId().equals(userId)) return ResponseEntity.status(403).build();
        String status = ENCODE_STATUS.getOrDefault(id, "IDLE");
        return ResponseEntity.ok(Map.of("status", status));
    }

    @GetMapping("/videos/encode-statuses")
    public ResponseEntity<?> encodeStatuses(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (java.util.Map.Entry<Long, String> entry : ENCODE_STATUS.entrySet()) {
            Optional<Video> opt = videoRepository.findById(entry.getKey());
            if (opt.isPresent() && opt.get().getOwnerId().equals(userId)) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/videos/{id}/replace-video")
    public ResponseEntity<?> replaceVideo(
            @PathVariable Long id,
            @RequestParam("videoFile") MultipartFile videoFile,
            HttpSession session
    ) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null)
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다."));

        Optional<Video> opt = videoRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Video video = opt.get();
        if (!video.getOwnerId().equals(userId))
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "권한이 없습니다."));

        if (videoFile == null || videoFile.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "파일을 선택해줘."));

        String videoExt = extensionOf(videoFile.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_VIDEO_EXTS.contains(videoExt))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "허용되지 않는 영상 형식입니다."));
        if (videoFile.getSize() > MAX_VIDEO_BYTES)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "영상 파일 크기는 2GB를 초과할 수 없습니다."));

        try {
            String newUuid = UUID.randomUUID().toString();
            Path dirPath = Paths.get(videoDir).toAbsolutePath();
            Path origPath = dirPath.resolve(newUuid + "_orig" + videoExt);
            videoFile.transferTo(origPath);

            String oldVideoUrl = video.getVideoUrl();
            String oldUrl1080  = video.getVideoUrl1080();
            String oldUrl720   = video.getVideoUrl720();
            String oldUrl480   = video.getVideoUrl480();
            String oldUrl360   = video.getVideoUrl360();

            video.setVideoUrl1080(null);
            video.setVideoUrl720(null);
            video.setVideoUrl480(null);
            video.setVideoUrl360(null);
            videoRepository.save(video);

            setEncodeStatus(id, "QUEUED");
            final Long videoId = id;
            final String ffmpeg = ffmpegPath;
            CompletableFuture.runAsync(() -> replaceVideoBackground(
                    ffmpeg, newUuid, videoExt, dirPath, videoId,
                    oldVideoUrl, oldUrl1080, oldUrl720, oldUrl480, oldUrl360));

            return ResponseEntity.ok(Map.of("success", true, "message", "업로드 완료, 인코딩을 시작합니다."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "업로드 중 오류: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/migrate-to-r2/status")
    public ResponseEntity<?> migrateToR2Status(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false));
        }
        List<Video> all = videoRepository.findAll().stream()
                .filter(v -> v.getVideoUrl() != null && !v.getVideoUrl().isBlank())
                .collect(Collectors.toList());
        long local = all.stream().filter(v -> v.getVideoUrl().startsWith("/uploads/")).count();
        long r2 = all.stream().filter(v -> v.getVideoUrl().startsWith("http")).count();
        return ResponseEntity.ok(Map.of("total", all.size(), "local", local, "r2", r2,
                "message", "마이그레이션 진행 중: " + r2 + "/" + all.size() + " 완료"));
    }

    private void migrateVideoFiles(Long videoId, Path videoDirPath, Path thumbDirPath) {
        videoRepository.findById(videoId).ifPresent(video -> {
            try {
                String newVideoUrl = migrateLocalFile(video.getVideoUrl(), videoDirPath, "video/mp4");
                String newThumb    = migrateLocalFile(video.getThumbnail(), thumbDirPath, null);
                String new1080     = migrateLocalFile(video.getVideoUrl1080(), videoDirPath, "video/mp4");
                String new720      = migrateLocalFile(video.getVideoUrl720(),  videoDirPath, "video/mp4");
                String new480      = migrateLocalFile(video.getVideoUrl480(),  videoDirPath, "video/mp4");
                String new360      = migrateLocalFile(video.getVideoUrl360(),  videoDirPath, "video/mp4");

                if (newVideoUrl != null) video.setVideoUrl(newVideoUrl);
                if (newThumb    != null) video.setThumbnail(newThumb);
                if (new1080     != null) video.setVideoUrl1080(new1080);
                if (new720      != null) video.setVideoUrl720(new720);
                if (new480      != null) video.setVideoUrl480(new480);
                if (new360      != null) video.setVideoUrl360(new360);

                videoRepository.save(video);
                System.out.println("[Migrate] 완료: videoId=" + videoId);
            } catch (Exception e) {
                System.err.println("[Migrate] 실패 videoId=" + videoId + ": " + e.getMessage());
            }
        });
    }

    private String migrateLocalFile(String url, Path baseDir, String fallbackContentType) {
        if (url == null || !url.startsWith("/uploads/")) return null;
        // /uploads/videos/xxx.mp4 → videos/xxx.mp4
        String key = url.substring("/uploads/".length());
        Path localFile = baseDir.getParent().resolve(key);
        if (!localFile.toFile().exists()) {
            System.err.println("[Migrate] 파일 없음: " + localFile);
            return null;
        }
        String ext = extensionOf(localFile.getFileName().toString());
        String contentType = fallbackContentType != null ? fallbackContentType : contentTypeFor(ext);
        try {
            String r2Url = storageService.upload(localFile, key, contentType);
            System.out.println("[Migrate] 업로드 완료: " + key);
            return r2Url;
        } catch (Exception e) {
            System.err.println("[Migrate] R2 업로드 실패 [" + key + "]: " + e.getMessage());
            return null;
        }
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
        Path origPath  = findOrigFile(dirPath, uuid);
        Path servePath = dirPath.resolve(uuid + ".mp4");
        Path tmpPath   = dirPath.resolve(uuid + "_conv.mp4");

        if (origPath == null) {
            // 원본 없음 — 이미 변환된 servePath로 변환만
            generateResolutionVariants(ffmpeg, servePath.toString(), dirPath, uuid, videoId);
            if (storageService.isConfigured() && servePath.toFile().exists()) {
                try { storageService.upload(servePath, "videos/" + uuid + ".mp4", "video/mp4"); Files.deleteIfExists(servePath); } catch (Exception ignored) {}
            }
            return;
        }

        setEncodeStatus(videoId, "CONVERTING");

        // 소스 해상도 확인 후 변환 대상 결정
        int sourceHeight = getSourceHeight(ffmpeg, origPath.toString());
        int[] allHeights = {1080, 720, 480, 360};
        java.util.LinkedHashMap<Integer, Path> varTargets = new java.util.LinkedHashMap<>();
        for (int h : allHeights) {
            if (h <= sourceHeight) varTargets.put(h, dirPath.resolve(uuid + "_" + h + "p.mp4"));
        }
        List<Integer> heights = new ArrayList<>(varTargets.keySet());
        int nVar = heights.size();

        // 단일 FFmpeg 패스: 메인 H.264 + 모든 해상도 변환
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg); cmd.add("-y");
        cmd.add("-i"); cmd.add(origPath.toString());

        if (nVar > 0) {
            int nTotal = nVar + 1;
            StringBuilder fc = new StringBuilder();
            fc.append("[0:v]split=").append(nTotal).append("[sv_main]");
            for (int i = 0; i < nVar; i++) fc.append("[sv").append(i).append("]");
            fc.append(";[sv_main]null[ov_main]");
            for (int i = 0; i < nVar; i++) {
                fc.append(";[sv").append(i).append("]scale=-2:").append(heights.get(i)).append("[ov").append(i).append("]");
            }
            cmd.add("-filter_complex"); cmd.add(fc.toString());
            // 메인 출력 (fast/crf23 — 원본 화질)
            cmd.add("-map"); cmd.add("[ov_main]"); cmd.add("-map"); cmd.add("0:a?");
            addEncoderArgs(cmd, true);
            cmd.add("-c:a"); cmd.add("aac"); cmd.add("-b:a"); cmd.add("128k"); cmd.add("-movflags"); cmd.add("+faststart");
            cmd.add(tmpPath.toString());
            // 해상도 변환 출력
            for (int i = 0; i < nVar; i++) {
                int h = heights.get(i);
                cmd.add("-map"); cmd.add("[ov" + i + "]"); cmd.add("-map"); cmd.add("0:a?");
                addEncoderArgs(cmd, false);
                cmd.add("-c:a"); cmd.add("aac"); cmd.add("-b:a"); cmd.add("96k"); cmd.add("-movflags"); cmd.add("+faststart");
                cmd.add(varTargets.get(h).toString());
            }
        } else {
            // 변환 대상 해상도 없음 — 단순 H.264 변환만
            addEncoderArgs(cmd, true);
            cmd.add("-c:a"); cmd.add("aac"); cmd.add("-b:a"); cmd.add("128k"); cmd.add("-movflags"); cmd.add("+faststart");
            cmd.add(tmpPath.toString());
        }

        System.out.println("[Encode] 단일 패스 시작 (메인+" + nVar + "변환) [" + uuid + "]");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            Thread drain = new Thread(() -> { try { process.getInputStream().transferTo(OutputStream.nullOutputStream()); } catch (Exception ignored) {} });
            drain.setDaemon(true); drain.start();

            boolean done = process.waitFor(90, TimeUnit.MINUTES);
            int exitCode = done ? process.exitValue() : -1;
            System.out.println("[Encode] 단일 패스 종료코드=" + exitCode + " [" + uuid + "]");

            if (done && exitCode == 0) {
                Files.move(tmpPath, servePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(origPath);
            } else {
                Files.deleteIfExists(tmpPath);
                for (Path varPath : varTargets.values()) try { Files.deleteIfExists(varPath); } catch (Exception ignored) {}
                setEncodeStatus(videoId, "ERROR:FFmpeg exit " + exitCode);
                return;
            }
        } catch (Exception e) {
            try { Files.deleteIfExists(tmpPath); } catch (Exception ignored) {}
            setEncodeStatus(videoId, "ERROR:" + e.getMessage());
            return;
        }

        // 썸네일 자동 생성
        videoRepository.findById(videoId).ifPresent(v -> {
            if (v.getThumbnail() == null || v.getThumbnail().isBlank()) {
                String autoThumb = generateAutoThumbnail(ffmpeg, servePath, dirPath, uuid);
                if (autoThumb != null) { v.setThumbnail(autoThumb); videoRepository.save(v); System.out.println("[Thumb] 자동 썸네일 저장 [" + uuid + "]"); }
            }
        });

        // 변환된 해상도 파일 R2 업로드 + DB 저장
        setEncodeStatus(videoId, "ENCODING");
        for (int h : heights) {
            Path varPath = varTargets.get(h);
            if (!varPath.toFile().exists()) continue;
            String url;
            if (storageService.isConfigured()) {
                try { url = storageService.upload(varPath, "videos/" + uuid + "_" + h + "p.mp4", "video/mp4"); Files.deleteIfExists(varPath); }
                catch (Exception e) { System.err.println("[Encode] R2 업로드 실패 " + h + "p [" + uuid + "]: " + e.getMessage()); continue; }
            } else { url = "/uploads/videos/" + uuid + "_" + h + "p.mp4"; }
            final String finalUrl = url; final int finalH = h;
            videoRepository.findById(videoId).ifPresent(v -> {
                if (finalH == 1080) v.setVideoUrl1080(finalUrl);
                else if (finalH == 720) v.setVideoUrl720(finalUrl);
                else if (finalH == 480) v.setVideoUrl480(finalUrl);
                else if (finalH == 360) v.setVideoUrl360(finalUrl);
                videoRepository.save(v);
            });
            System.out.println("[Encode] " + h + "p 완료 [" + uuid + "]");
        }

        // 메인 파일 R2 업로드
        if (storageService.isConfigured() && servePath.toFile().exists()) {
            try { storageService.upload(servePath, "videos/" + uuid + ".mp4", "video/mp4"); Files.deleteIfExists(servePath); } catch (Exception ignored) {}
        }
        setEncodeStatus(videoId, "DONE");
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
        if (!sourceInput.startsWith("http") && !new java.io.File(sourceInput).exists()) return;

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
            }
        }

        int sourceHeight = getSourceHeight(ffmpeg, sourceInput);
        int[] allHeights = {1080, 720, 480, 360};

        // 출력할 해상도 결정
        java.util.LinkedHashMap<Integer, Path> targets = new java.util.LinkedHashMap<>();
        for (int h : allHeights) {
            if (h <= sourceHeight) targets.put(h, dirPath.resolve(uuid + "_" + h + "p.mp4"));
        }

        if (targets.isEmpty()) {
            if (downloadedTemp != null) try { Files.deleteIfExists(downloadedTemp); } catch (Exception ignored) {}
            return;
        }

        // filter_complex로 단일 디코딩 멀티 출력
        List<Integer> heights = new ArrayList<>(targets.keySet());
        int n = heights.size();

        StringBuilder fc = new StringBuilder();
        if (n == 1) {
            fc.append("[0:v]scale=-2:").append(heights.get(0)).append("[ov0]");
        } else {
            fc.append("[0:v]split=").append(n);
            for (int i = 0; i < n; i++) fc.append("[sv").append(i).append("]");
            fc.append(";");
            for (int i = 0; i < n; i++) {
                if (i > 0) fc.append(";");
                fc.append("[sv").append(i).append("]scale=-2:").append(heights.get(i)).append("[ov").append(i).append("]");
            }
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg); cmd.add("-y");
        cmd.add("-i"); cmd.add(sourceInput);
        cmd.add("-filter_complex"); cmd.add(fc.toString());
        for (int i = 0; i < n; i++) {
            int h = heights.get(i);
            cmd.add("-map"); cmd.add("[ov" + i + "]");
            cmd.add("-map"); cmd.add("0:a?");
            addEncoderArgs(cmd, false);
            cmd.add("-c:a");    cmd.add("aac");
            cmd.add("-b:a");    cmd.add("96k");
            cmd.add("-movflags"); cmd.add("+faststart");
            cmd.add(targets.get(h).toString());
        }

        setEncodeStatus(videoId, "ENCODING");
        try {
            System.out.println("[Batch] filter_complex 멀티 출력 시작 " + targets.keySet() + " [" + uuid + "]");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            Thread drain = new Thread(() -> {
                try { process.getInputStream().transferTo(OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            });
            drain.setDaemon(true);
            drain.start();

            boolean done = process.waitFor(120, TimeUnit.MINUTES);
            int exitCode = done ? process.exitValue() : -1;
            System.out.println("[Batch] 멀티 출력 종료코드=" + exitCode + " [" + uuid + "]");

            if (done && exitCode == 0) {
                for (java.util.Map.Entry<Integer, Path> entry : targets.entrySet()) {
                    int h = entry.getKey();
                    Path variantPath = entry.getValue();
                    if (!variantPath.toFile().exists()) continue;

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
            }
        } catch (Exception e) {
            System.err.println("[Batch] 멀티 출력 오류 [" + uuid + "]: " + e.getMessage());
            setEncodeStatus(videoId, "ERROR:" + e.getMessage());
        } finally {
            if (downloadedTemp != null) try { Files.deleteIfExists(downloadedTemp); } catch (Exception ignored) {}
        }
        if ("ENCODING".equals(ENCODE_STATUS.get(videoId))) setEncodeStatus(videoId, "DONE");
    }

    private void replaceVideoBackground(String ffmpeg, String newUuid, String origExt,
                                         Path dirPath, Long videoId,
                                         String oldVideoUrl, String oldUrl1080,
                                         String oldUrl720, String oldUrl480, String oldUrl360) {
        Path origPath  = dirPath.resolve(newUuid + "_orig" + origExt);
        Path servePath = dirPath.resolve(newUuid + ".mp4");
        Path tmpPath   = dirPath.resolve(newUuid + "_conv.mp4");
        try {
            setEncodeStatus(videoId, "CONVERTING");

            // 소스 해상도 확인 후 변환 대상 결정
            int sourceHeight = getSourceHeight(ffmpeg, origPath.toString());
            int[] orderedHeights = {1080, 720, 480, 360};
            String[] oldUrls = {oldUrl1080, oldUrl720, oldUrl480, oldUrl360};
            java.util.LinkedHashMap<Integer, Path> varTargets = new java.util.LinkedHashMap<>();
            for (int h : orderedHeights) {
                if (h <= sourceHeight) varTargets.put(h, dirPath.resolve(newUuid + "_" + h + "p.mp4"));
            }
            List<Integer> heights = new ArrayList<>(varTargets.keySet());
            int nVar = heights.size();

            // 단일 FFmpeg 패스: 메인 H.264 + 모든 해상도 변환
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpeg); cmd.add("-y");
            cmd.add("-i"); cmd.add(origPath.toString());

            if (nVar > 0) {
                int nTotal = nVar + 1;
                StringBuilder fc = new StringBuilder();
                fc.append("[0:v]split=").append(nTotal).append("[sv_main]");
                for (int i = 0; i < nVar; i++) fc.append("[sv").append(i).append("]");
                fc.append(";[sv_main]null[ov_main]");
                for (int i = 0; i < nVar; i++) {
                    fc.append(";[sv").append(i).append("]scale=-2:").append(heights.get(i)).append("[ov").append(i).append("]");
                }
                cmd.add("-filter_complex"); cmd.add(fc.toString());
                cmd.add("-map"); cmd.add("[ov_main]"); cmd.add("-map"); cmd.add("0:a?");
                addEncoderArgs(cmd, true);
                cmd.add("-c:a"); cmd.add("aac"); cmd.add("-b:a"); cmd.add("128k"); cmd.add("-movflags"); cmd.add("+faststart");
                cmd.add(tmpPath.toString());
                for (int i = 0; i < nVar; i++) {
                    int h = heights.get(i);
                    cmd.add("-map"); cmd.add("[ov" + i + "]"); cmd.add("-map"); cmd.add("0:a?");
                    addEncoderArgs(cmd, false);
                    cmd.add("-c:a"); cmd.add("aac"); cmd.add("-b:a"); cmd.add("96k"); cmd.add("-movflags"); cmd.add("+faststart");
                    cmd.add(varTargets.get(h).toString());
                }
            } else {
                addEncoderArgs(cmd, true);
                cmd.add("-c:a"); cmd.add("aac"); cmd.add("-b:a"); cmd.add("128k"); cmd.add("-movflags"); cmd.add("+faststart");
                cmd.add(tmpPath.toString());
            }

            System.out.println("[Replace] 단일 패스 시작 (메인+" + nVar + "변환) [" + newUuid + "]");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            Thread drain = new Thread(() -> { try { proc.getInputStream().transferTo(OutputStream.nullOutputStream()); } catch (Exception ignored) {} });
            drain.setDaemon(true); drain.start();

            boolean convDone = proc.waitFor(90, TimeUnit.MINUTES);
            int exitCode = convDone ? proc.exitValue() : -1;
            System.out.println("[Replace] 단일 패스 종료코드=" + exitCode + " [" + newUuid + "]");

            if (convDone && exitCode == 0) {
                Files.move(tmpPath, servePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(origPath);
            } else {
                Files.deleteIfExists(tmpPath);
                for (Path vp : varTargets.values()) try { Files.deleteIfExists(vp); } catch (Exception ignored) {}
                if (origPath.toFile().exists())
                    Files.move(origPath, servePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                setEncodeStatus(videoId, "ERROR:FFmpeg exit " + exitCode);
                return;
            }

            // 변환된 해상도 파일 R2 업로드 + DB 저장
            setEncodeStatus(videoId, "ENCODING");
            List<String> newVariantUrls = new ArrayList<>();
            for (int h : orderedHeights) {
                Path varPath = varTargets.get(h);
                if (varPath == null || !varPath.toFile().exists()) { newVariantUrls.add(null); continue; }
                String url;
                if (storageService.isConfigured()) {
                    url = storageService.upload(varPath, "videos/" + newUuid + "_" + h + "p.mp4", "video/mp4");
                    Files.deleteIfExists(varPath);
                } else { url = "/uploads/videos/" + newUuid + "_" + h + "p.mp4"; }
                newVariantUrls.add(url);
                final String fu = url; final int fh = h;
                videoRepository.findById(videoId).ifPresent(v -> {
                    if (fh == 1080) v.setVideoUrl1080(fu);
                    else if (fh == 720) v.setVideoUrl720(fu);
                    else if (fh == 480) v.setVideoUrl480(fu);
                    else if (fh == 360) v.setVideoUrl360(fu);
                    videoRepository.save(v);
                });
                System.out.println("[Replace] " + h + "p 완료 [" + newUuid + "]");
            }

            // 메인 파일 R2 업로드 + DB 갱신
            setEncodeStatus(videoId, "UPLOADING");
            String newVideoUrl;
            if (storageService.isConfigured()) {
                newVideoUrl = storageService.upload(servePath, "videos/" + newUuid + ".mp4", "video/mp4");
                Files.deleteIfExists(servePath);
            } else { newVideoUrl = "/uploads/videos/" + newUuid + ".mp4"; }
            final String fUrl = newVideoUrl;
            videoRepository.findById(videoId).ifPresent(v -> { v.setVideoUrl(fUrl); videoRepository.save(v); });

            // 구 파일 R2 삭제
            if (storageService.isConfigured()) {
                storageService.delete(oldVideoUrl);
                for (int i = 0; i < oldUrls.length; i++) {
                    if (newVariantUrls.size() > i && newVariantUrls.get(i) != null)
                        storageService.delete(oldUrls[i]);
                }
            }

            setEncodeStatus(videoId, "DONE");
            System.out.println("[Replace] 완전 완료 [" + newUuid + "]");
        } catch (Exception e) {
            setEncodeStatus(videoId, "ERROR:" + e.getMessage());
            System.err.println("[Replace] 오류 [" + newUuid + "]: " + e.getMessage());
            try { Files.deleteIfExists(tmpPath); } catch (Exception ignored) {}
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

    private String generateAutoThumbnail(String ffmpeg, Path videoPath, Path dirPath, String uuid) {
        if (!videoPath.toFile().exists()) return null;
        Path thumbPath = dirPath.resolve(uuid + "_thumb.jpg");
        try {
            double duration = getSourceDuration(ffmpeg, videoPath.toString());
            double seekTime = duration > 10 ? duration * 0.1 : Math.max(duration * 0.5, 0);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y",
                    "-ss", String.format("%.2f", seekTime),
                    "-i", videoPath.toString(),
                    "-vframes", "1",
                    "-q:v", "2",
                    "-vf", "scale=1280:-2",
                    thumbPath.toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done || p.exitValue() != 0 || !thumbPath.toFile().exists()) return null;

            if (storageService.isConfigured()) {
                String url = storageService.upload(thumbPath, "thumbnails/" + uuid + "_thumb.jpg", "image/jpeg");
                Files.deleteIfExists(thumbPath);
                return url;
            } else {
                Path destPath = Paths.get(thumbnailDir).toAbsolutePath().resolve(uuid + "_thumb.jpg");
                Files.move(thumbPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return "/uploads/thumbnails/" + uuid + "_thumb.jpg";
            }
        } catch (Exception e) {
            System.err.println("[Thumb] 자동 썸네일 생성 실패 [" + uuid + "]: " + e.getMessage());
            try { Files.deleteIfExists(thumbPath); } catch (Exception ignored) {}
            return null;
        }
    }

    private double getSourceDuration(String ffmpeg, String sourceInput) {
        try {
            String ffprobe = ffmpeg.endsWith("ffmpeg") ? ffmpeg.replace("ffmpeg", "ffprobe")
                           : ffmpeg.endsWith("ffmpeg.exe") ? ffmpeg.replace("ffmpeg.exe", "ffprobe.exe")
                           : "ffprobe";
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobe, "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "csv=p=0",
                    sourceInput
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(30, TimeUnit.SECONDS);
            return Double.parseDouble(out.split("\n")[0].trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void addEncoderArgs(List<String> cmd, boolean highQuality) {
        String hw = ffmpegHwAccel == null ? "none" : ffmpegHwAccel.trim().toLowerCase();
        int quality = highQuality ? 23 : 26;
        switch (hw) {
            case "nvenc" -> {
                cmd.add("-c:v"); cmd.add("h264_nvenc");
                cmd.add("-preset"); cmd.add(highQuality ? "p4" : "p1");
                cmd.add("-cq"); cmd.add(String.valueOf(quality));
            }
            case "qsv" -> {
                cmd.add("-c:v"); cmd.add("h264_qsv");
                cmd.add("-preset"); cmd.add(highQuality ? "medium" : "veryfast");
                cmd.add("-global_quality"); cmd.add(String.valueOf(quality));
            }
            case "amf" -> {
                cmd.add("-c:v"); cmd.add("h264_amf");
                cmd.add("-quality"); cmd.add(highQuality ? "quality" : "speed");
                cmd.add("-rc"); cmd.add("cqp");
                cmd.add("-qp_i"); cmd.add(String.valueOf(quality));
                cmd.add("-qp_p"); cmd.add(String.valueOf(quality));
            }
            default -> {
                cmd.add("-c:v"); cmd.add("libx264");
                cmd.add("-preset"); cmd.add(highQuality ? "fast" : "ultrafast");
                cmd.add("-crf"); cmd.add(String.valueOf(quality));
            }
        }
    }

    private static void setEncodeStatus(Long videoId, String status) {
        ENCODE_STATUS.put(videoId, status);
        ENCODE_STATUS_TIME.put(videoId, System.currentTimeMillis());
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000)
    public void cleanupEncodeStatus() {
        long cutoff = System.currentTimeMillis() - 10 * 60 * 1000L;
        ENCODE_STATUS_TIME.entrySet().removeIf(e -> {
            String status = ENCODE_STATUS.get(e.getKey());
            boolean expired = e.getValue() < cutoff
                    && (status == null || "DONE".equals(status) || status.startsWith("ERROR"));
            if (expired) ENCODE_STATUS.remove(e.getKey());
            return expired;
        });
    }
}
