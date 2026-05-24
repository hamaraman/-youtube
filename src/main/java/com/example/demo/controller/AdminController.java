package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.DataInitializer;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public AdminController(VideoRepository videoRepository, UserRepository userRepository,
                           AdminChecker adminChecker, LoginUserResolver loginUserResolver,
                           DataInitializer dataInitializer) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.adminChecker = adminChecker;
        this.loginUserResolver = loginUserResolver;
        this.dataInitializer = dataInitializer;
    }

    @GetMapping("/videos")
    public ResponseEntity<?> listVideos(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        List<Map<String, Object>> result = videoRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "title", v.getTitle(),
                        "channel", v.getChannel(),
                        "category", v.getCategory() == null ? "" : v.getCategory(),
                        "date", v.getDateText() == null ? "" : v.getDateText(),
                        "ownerId", v.getOwnerId() == null ? "" : v.getOwnerId(),
                        "visibility", v.getVisibility() == null ? "" : v.getVisibility()
                ))
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

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        List<Map<String, Object>> result = userRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "nickname", u.getNickname() == null ? "" : u.getNickname(),
                        "channelName", u.getChannelName() == null ? "" : u.getChannelName(),
                        "email", u.getEmail() == null ? "" : u.getEmail()
                ))
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
}
