package com.example.demo.controller;

import com.example.demo.service.VideoShareService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/share/video")
public class VideoShareController {

    private final VideoShareService videoShareService;

    public VideoShareController(VideoShareService videoShareService) {
        this.videoShareService = videoShareService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> sharePage(
            @PathVariable Long id,
            @RequestParam(required = false) Integer t,
            HttpServletRequest request) {

        String origin = buildOrigin(request);

        try {
            String html = videoShareService.generateShareHtml(id, t, origin);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (ResponseStatusException e) {
            String errorMessage = e.getReason() != null ? e.getReason() : "영상을 찾을 수 없습니다.";
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body>" + errorMessage + "</body></html>");
        }
    }

    private static String buildOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }
}
