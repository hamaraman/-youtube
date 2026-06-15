package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminChecker adminChecker;
    private final LoginUserResolver loginUserResolver;

    public AdminController(AdminService adminService, AdminChecker adminChecker,
                           LoginUserResolver loginUserResolver) {
        this.adminService = adminService;
        this.adminChecker = adminChecker;
        this.loginUserResolver = loginUserResolver;
    }

    @GetMapping("/videos/search")
    public ResponseEntity<?> searchVideos(@RequestParam String title, HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        return ResponseEntity.ok(adminService.searchVideos(title));
    }

    @GetMapping("/videos")
    public ResponseEntity<?> listVideos(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        return ResponseEntity.ok(adminService.listVideos());
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<?> deleteVideo(@PathVariable Long id, HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "관리자 권한이 필요합니다."));
        }
        try {
            adminService.deleteVideo(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "삭제됐습니다."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("success", false, "message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "삭제 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/videos/broken")
    public ResponseEntity<?> listBrokenVideos(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        return ResponseEntity.ok(adminService.listBrokenVideos());
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
        try {
            int count = adminService.bulkDeleteVideos(ids);
            return ResponseEntity.ok(Map.of("success", true, "message", count + "개 삭제됐습니다.", "count", count));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "삭제 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        return ResponseEntity.ok(adminService.listUsers());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        AuthController.SessionUser me = loginUserResolver.getUser(session);
        Long meId = me != null ? me.getId() : null;
        try {
            adminService.deleteUser(id, meId);
            return ResponseEntity.ok(Map.of("success", true, "message", "삭제됐습니다."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
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
        String role = body.getOrDefault("role", "USER");
        try {
            adminService.setUserRole(id, role);
            return ResponseEntity.ok(Map.of("success", true, "role", role));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }
}
