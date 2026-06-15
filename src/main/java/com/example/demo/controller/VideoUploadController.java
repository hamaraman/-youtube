package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.service.VideoUploadService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class VideoUploadController {

    private final VideoUploadService videoUploadService;
    private final LoginUserResolver loginUserResolver;
    private final AdminChecker adminChecker;

    public VideoUploadController(VideoUploadService videoUploadService,
                                 LoginUserResolver loginUserResolver,
                                 AdminChecker adminChecker) {
        this.videoUploadService = videoUploadService;
        this.loginUserResolver = loginUserResolver;
        this.adminChecker = adminChecker;
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
            SessionUser sessionUser = loginUserResolver.getUser(session);
            UploadResponse response = videoUploadService.uploadVideo(
                    title, description, channel, duration, visibility, avatar, category,
                    embedUrl, thumbnailUrl, videoFile, thumbnailFile, sessionUser
            );
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            UploadResponse response = new UploadResponse();
            response.setSuccess(false);
            response.setMessage(e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(response);
        } catch (Exception e) {
            UploadResponse response = new UploadResponse();
            response.setSuccess(false);
            response.setMessage("업로드 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/admin/generate-resolutions")
    public ResponseEntity<?> generateResolutionsForAll(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "관리자 권한이 필요합니다."));
        }
        int queued = videoUploadService.generateResolutionsForAll();
        return ResponseEntity.ok(Map.of("success", true, "queued", queued,
                "message", queued + "개 영상의 해상도 변환을 백그라운드에서 시작했습니다."));
    }

    @PostMapping("/admin/migrate-to-r2")
    public ResponseEntity<?> migrateToR2(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "관리자 권한이 필요합니다."));
        }
        try {
            int queued = videoUploadService.migrateToR2();
            return ResponseEntity.ok(Map.of("success", true, "queued", queued,
                    "message", queued + "개 영상의 R2 마이그레이션을 백그라운드에서 시작했습니다."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("success", false, "message", e.getReason()));
        }
    }

    @GetMapping("/videos/{id}/encode-status")
    public ResponseEntity<?> encodeStatus(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();
        try {
            String status = videoUploadService.getEncodeStatus(id, userId);
            return ResponseEntity.ok(Map.of("status", status));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @GetMapping("/videos/encode-statuses")
    public ResponseEntity<?> encodeStatuses(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();
        Map<String, String> statuses = videoUploadService.getEncodeStatuses(userId);
        return ResponseEntity.ok(statuses);
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
        try {
            videoUploadService.replaceVideo(id, videoFile, userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "업로드 완료, 인코딩을 시작합니다."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("success", false, "message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "업로드 중 오류: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/migrate-to-r2/status")
    public ResponseEntity<?> migrateToR2Status(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false));
        }
        Map<String, Object> status = videoUploadService.getMigrateStatus();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/admin/generate-resolutions/status")
    public ResponseEntity<?> generateResolutionsStatus(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false));
        }
        Map<String, Object> status = videoUploadService.getGenerateResolutionsStatus();
        return ResponseEntity.ok(status);
    }
}
