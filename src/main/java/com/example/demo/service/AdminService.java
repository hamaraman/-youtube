package com.example.demo.service;

import com.example.demo.config.DataInitializer;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoReport;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoReportRepository;
import com.example.demo.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final VideoReportRepository videoReportRepository;
    private final DataInitializer dataInitializer;

    @Value("${file.video-dir}")
    private String videoDir;

    public AdminService(VideoRepository videoRepository, UserRepository userRepository,
                        VideoReportRepository videoReportRepository,
                        DataInitializer dataInitializer) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.videoReportRepository = videoReportRepository;
        this.dataInitializer = dataInitializer;
    }

    public List<Map<String, Object>> listReports() {
        return videoReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toReportMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toReportMap(VideoReport r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("videoId", r.getVideoId());
        Video v = r.getVideoId() == null ? null : videoRepository.findById(r.getVideoId()).orElse(null);
        m.put("videoTitle", v != null ? v.getTitle() : "(삭제된 영상)");
        m.put("thumbnail", v != null && v.getThumbnail() != null ? v.getThumbnail() : "");
        m.put("reporterId", r.getReporterId() == null ? "" : r.getReporterId());
        User reporter = r.getReporterId() == null ? null : userRepository.findById(r.getReporterId()).orElse(null);
        m.put("reporter", reporter != null
                ? (reporter.getNickname() != null ? reporter.getNickname() : reporter.getUsername())
                : "(탈퇴한 사용자)");
        m.put("reason", r.getReason());
        m.put("detail", r.getDetail() == null ? "" : r.getDetail());
        m.put("time", r.getTime());
        return m;
    }

    public List<Map<String, Object>> searchVideos(String title) {
        return videoRepository.findByTitleContaining(title).stream()
                .map(v -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("title", v.getTitle());
                    m.put("channel", v.getChannel());
                    m.put("ownerId", v.getOwnerId() == null ? "" : v.getOwnerId());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listVideos() {
        return videoRepository.findAll().stream()
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
    }

    public void deleteVideo(Long id) {
        if (!videoRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다.");
        }
        dataInitializer.deleteVideoAndRelated(id);
    }

    public List<Map<String, Object>> listBrokenVideos() {
        Path videoBasePath = Paths.get(videoDir).toAbsolutePath();
        return videoRepository.findAll().stream()
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
    }

    public int bulkDeleteVideos(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            if (videoRepository.existsById(id)) {
                dataInitializer.deleteVideoAndRelated(id);
                count++;
            }
        }
        return count;
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

    public List<Map<String, Object>> listUsers() {
        return userRepository.findAll().stream()
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
    }

    public void deleteUser(Long id, Long meId) {
        if (meId != null && meId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 삭제할 수 없습니다.");
        }
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        // 해당 유저의 영상 먼저 삭제
        videoRepository.findAll().stream()
                .filter(v -> id.equals(v.getOwnerId()))
                .forEach(v -> dataInitializer.deleteVideoAndRelated(v.getId()));
        userRepository.deleteById(id);
    }

    public void setUserRole(Long id, String role) {
        String upperRole = role.toUpperCase();
        if (!upperRole.equals("ADMIN") && !upperRole.equals("USER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 role입니다.");
        }
        userRepository.findById(id).map(user -> {
            user.setRole(upperRole);
            return userRepository.save(user);
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
