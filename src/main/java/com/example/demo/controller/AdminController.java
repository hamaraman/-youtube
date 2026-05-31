package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.DataInitializer;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final AdminChecker adminChecker;
    private final LoginUserResolver loginUserResolver;
    private final DataInitializer dataInitializer;

    @Value("${file.video-dir}")
    private String videoDir;

    public AdminController(VideoRepository videoRepository, UserRepository userRepository,
                           AdminChecker adminChecker, LoginUserResolver loginUserResolver,
                           DataInitializer dataInitializer) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.adminChecker = adminChecker;
        this.loginUserResolver = loginUserResolver;
        this.dataInitializer = dataInitializer;
    }

    @GetMapping("/videos/search")
    public ResponseEntity<?> searchVideos(@RequestParam String title, HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        List<Map<String, Object>> result = videoRepository.findByTitleContaining(title).stream()
                .map(v -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("title", v.getTitle());
                    m.put("channel", v.getChannel());
                    m.put("ownerId", v.getOwnerId() == null ? "" : v.getOwnerId());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/videos")
    public ResponseEntity<?> listVideos(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        List<Map<String, Object>> result = videoRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(v -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("title", v.getTitle());
                    m.put("channel", v.getChannel());
                    m.put("category", v.getCategory() == null ? "" : v.getCategory());
                    m.put("date", v.getDateText() == null ? "" : v.getDateText());
                    m.put("ownerId", v.getOwnerId() == null ? "" : v.getOwnerId());
                    m.put("visibility", v.getVisibility() == null ? "" : v.getVisibility());
                    m.put("thumbnail", v.getThumbnail() == null ? "" : v.getThumbnail());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<?> deleteVideo(@PathVariable Long id, HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "관리자 권한이 필요합니다."));
        }
        if (!videoRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "영상을 찾을 수 없습니다."));
        }
        try {
            dataInitializer.deleteVideoAndRelated(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "삭제됐습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "삭제 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/videos/broken")
    public ResponseEntity<?> listBrokenVideos(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        Path videoBasePath = Paths.get(videoDir).toAbsolutePath();
        List<Map<String, Object>> result = videoRepository.findAll().stream()
                .filter(v -> isBroken(v, videoBasePath))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(v -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("title", v.getTitle());
                    m.put("channel", v.getChannel());
                    m.put("videoUrl", v.getVideoUrl() == null ? "" : v.getVideoUrl());
                    m.put("reason", getBrokenReason(v, videoBasePath));
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/videos/bulk-delete")
    public ResponseEntity<?> bulkDeleteVideos(@RequestBody Map<String, List<Long>> body, HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "관리자 권한이 필요합니다."));
        }
        List<Long> ids = body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "삭제할 영상을 선택해줘."));
        }
        int count = 0;
        for (Long id : ids) {
            if (videoRepository.existsById(id)) {
                dataInitializer.deleteVideoAndRelated(id);
                count++;
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "message", count + "개 삭제됐습니다.", "count", count));
    }

    private boolean isBroken(Video v, Path videoBasePath) {
        String videoUrl = v.getVideoUrl();
        String embedUrl = v.getEmbedUrl();
        if ((videoUrl == null || videoUrl.isBlank()) && (embedUrl == null || embedUrl.isBlank())) {
            return true;
        }
        if (videoUrl != null && videoUrl.startsWith("/uploads/videos/")) {
            String filename = videoUrl.substring("/uploads/videos/".length());
            return !Files.exists(videoBasePath.resolve(filename));
        }
        return false;
    }

    private String getBrokenReason(Video v, Path videoBasePath) {
        String videoUrl = v.getVideoUrl();
        String embedUrl = v.getEmbedUrl();
        if ((videoUrl == null || videoUrl.isBlank()) && (embedUrl == null || embedUrl.isBlank())) {
            return "영상 소스 없음";
        }
        if (videoUrl != null && videoUrl.startsWith("/uploads/videos/")) {
            return "파일 없음 (로컬)";
        }
        return "알 수 없음";
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        List<Map<String, Object>> result = userRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("nickname", u.getNickname() == null ? "" : u.getNickname());
                    m.put("channelName", u.getChannelName() == null ? "" : u.getChannelName());
                    m.put("email", u.getEmail() == null ? "" : u.getEmail());
                    m.put("role", u.getRole() == null ? "USER" : u.getRole());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        AuthController.SessionUser me = loginUserResolver.getUser(session);
        if (me != null && me.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "자기 자신은 삭제할 수 없습니다."));
        }
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            // 해당 유저의 영상 먼저 삭제
            videoRepository.findAll().stream()
                    .filter(v -> id.equals(v.getOwnerId()))
                    .forEach(v -> dataInitializer.deleteVideoAndRelated(v.getId()));
            userRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "삭제됐습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "삭제 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/role")
    public ResponseEntity<?> setUserRole(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        String role = body.getOrDefault("role", "USER").toUpperCase();
        if (!role.equals("ADMIN") && !role.equals("USER")) {
            return ResponseEntity.badRequest().body(Map.of("message", "유효하지 않은 role입니다."));
        }
        return userRepository.findById(id).map(user -> {
            user.setRole(role);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "username", user.getUsername(), "role", role));
        }).orElse(ResponseEntity.notFound().build());
    }
}
