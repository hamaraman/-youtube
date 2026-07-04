package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.AdminService;
import com.example.demo.service.NotificationService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminChecker adminChecker;
    private final LoginUserResolver loginUserResolver;
    private final NotificationService notificationService;

    public AdminController(AdminService adminService, AdminChecker adminChecker,
                           LoginUserResolver loginUserResolver,
                           NotificationService notificationService) {
        this.adminService = adminService;
        this.adminChecker = adminChecker;
        this.loginUserResolver = loginUserResolver;
        this.notificationService = notificationService;
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

    @GetMapping(value = "/diag/threads", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> threadDump(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body("관리자 권한이 필요합니다.");
        }
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        StringBuilder sb = new StringBuilder();
        for (ThreadInfo t : threadMXBean.dumpAllThreads(true, true)) {
            sb.append('"').append(t.getThreadName()).append('"')
              .append(" id=").append(t.getThreadId())
              .append(' ').append(t.getThreadState());
            if (t.getLockName() != null) sb.append(" on ").append(t.getLockName());
            if (t.getLockOwnerName() != null) {
                sb.append(" owned by \"").append(t.getLockOwnerName())
                  .append("\" id=").append(t.getLockOwnerId());
            }
            sb.append('\n');
            for (StackTraceElement e : t.getStackTrace()) {
                sb.append("    at ").append(e).append('\n');
            }
            sb.append('\n');
        }
        return ResponseEntity.ok(sb.toString());
    }

    @GetMapping("/diag/sse")
    public ResponseEntity<?> sseStatus(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        return ResponseEntity.ok(notificationService.emitterCounts());
    }

    @GetMapping("/diag/info")
    public ResponseEntity<?> runtimeInfo(HttpSession session) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
        }
        return ResponseEntity.ok(Map.of(
                "javaVersion", System.getProperty("java.version"),
                "javaVendor", System.getProperty("java.vendor"),
                "osName", System.getProperty("os.name"),
                "osArch", System.getProperty("os.arch"),
                "springBootVersion", String.valueOf(org.springframework.boot.SpringBootVersion.getVersion()),
                "tomcatVersion", org.apache.catalina.util.ServerInfo.getServerNumber()
        ));
    }

    // Tomcat이 청크 단위 flush를 실시간으로 클라이언트에 내보내는지 검사 (동기 쓰기)
    @GetMapping("/diag/flush-test")
    public void flushTest(HttpSession session, HttpServletResponse response) throws IOException {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            response.setStatus(403);
            return;
        }
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        ServletOutputStream out = response.getOutputStream();
        for (int i = 1; i <= 5; i++) {
            out.write(("sync-chunk-" + i + " t=" + System.currentTimeMillis() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    // 서블릿 async 컨텍스트에서의 flush 검사 (SseEmitter와 같은 비동기 쓰기 경로)
    @GetMapping("/diag/flush-test-async")
    public void flushTestAsync(HttpSession session, HttpServletRequest request,
                               HttpServletResponse response) {
        if (!adminChecker.isAdmin(session, loginUserResolver)) {
            response.setStatus(403);
            return;
        }
        AsyncContext ctx = request.startAsync();
        ctx.setTimeout(30_000);
        Thread writer = new Thread(() -> {
            try {
                HttpServletResponse res = (HttpServletResponse) ctx.getResponse();
                res.setContentType("text/plain;charset=UTF-8");
                ServletOutputStream out = res.getOutputStream();
                for (int i = 1; i <= 5; i++) {
                    out.write(("async-chunk-" + i + " t=" + System.currentTimeMillis() + "\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {
            } finally {
                try { ctx.complete(); } catch (Exception ignored) {}
            }
        });
        writer.setDaemon(true);
        writer.start();
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
