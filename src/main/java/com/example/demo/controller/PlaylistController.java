package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.Playlist;
import com.example.demo.entity.PlaylistVideo;
import com.example.demo.entity.Video;
import com.example.demo.repository.PlaylistRepository;
import com.example.demo.repository.PlaylistVideoRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistRepository playlistRepository;
    private final PlaylistVideoRepository playlistVideoRepository;
    private final VideoRepository videoRepository;
    private final LoginUserResolver loginUserResolver;

    public PlaylistController(PlaylistRepository playlistRepository,
                              PlaylistVideoRepository playlistVideoRepository,
                              VideoRepository videoRepository,
                              LoginUserResolver loginUserResolver) {
        this.playlistRepository = playlistRepository;
        this.playlistVideoRepository = playlistVideoRepository;
        this.videoRepository = videoRepository;
        this.loginUserResolver = loginUserResolver;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPlaylists(@PathVariable Long userId) {
        List<Playlist> playlists = playlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = playlists.stream().map(p -> {
            long count = playlistVideoRepository.countByPlaylistId(p.getId());
            String thumb = playlistVideoRepository.findByPlaylistIdOrderByAddedAtDesc(p.getId())
                    .stream().findFirst()
                    .flatMap(pv -> videoRepository.findById(pv.getVideoId()))
                    .map(Video::getThumbnail).orElse(null);
            return Map.<String, Object>of(
                    "id", p.getId(),
                    "name", p.getName(),
                    "videoCount", count,
                    "thumbnail", thumb != null ? thumb : ""
            );
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyPlaylists(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        List<Playlist> playlists = playlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = playlists.stream().map(p -> {
            long count = playlistVideoRepository.countByPlaylistId(p.getId());
            String thumb = playlistVideoRepository.findByPlaylistIdOrderByAddedAtDesc(p.getId())
                    .stream().findFirst()
                    .flatMap(pv -> videoRepository.findById(pv.getVideoId()))
                    .map(Video::getThumbnail).orElse(null);
            return Map.<String, Object>of(
                    "id", p.getId(),
                    "name", p.getName(),
                    "videoCount", count,
                    "thumbnail", thumb != null ? thumb : ""
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "재생목록 이름을 입력해줘."));

        Playlist p = new Playlist();
        p.setUserId(userId);
        p.setName(name);
        playlistRepository.save(p);

        return ResponseEntity.ok(Map.of("success", true, "id", p.getId(), "name", p.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        Optional<Playlist> opt = playlistRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getUserId().equals(userId)) return ResponseEntity.status(403).build();

        playlistVideoRepository.deleteByPlaylistId(id);
        playlistRepository.delete(opt.get());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/{id}/videos")
    public ResponseEntity<?> getPlaylistVideos(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        Optional<Playlist> opt = playlistRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getUserId().equals(userId)) return ResponseEntity.status(403).build();

        List<Map<String, Object>> videos = playlistVideoRepository.findByPlaylistIdOrderByAddedAtDesc(id)
                .stream()
                .map(pv -> videoRepository.findById(pv.getVideoId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "title", v.getTitle(),
                        "thumbnail", v.getThumbnail(),
                        "channel", v.getChannel(),
                        "duration", v.getDuration() != null ? v.getDuration() : "0:00",
                        "date", v.getDateText() != null ? v.getDateText() : "",
                        "viewCount", v.getViewCount()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("name", opt.get().getName(), "videos", videos));
    }

    @PostMapping("/{id}/videos")
    public ResponseEntity<?> addVideo(@PathVariable Long id, @RequestBody Map<String, Long> body, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        Optional<Playlist> opt = playlistRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getUserId().equals(userId)) return ResponseEntity.status(403).build();

        Long videoId = body.get("videoId");
        if (videoId == null || videoRepository.findById(videoId).isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "영상을 찾을 수 없어."));

        if (playlistVideoRepository.existsByPlaylistIdAndVideoId(id, videoId))
            return ResponseEntity.ok(Map.of("success", false, "message", "이미 추가된 영상이야."));

        PlaylistVideo pv = new PlaylistVideo();
        pv.setPlaylistId(id);
        pv.setVideoId(videoId);
        playlistVideoRepository.save(pv);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}/videos/{videoId}")
    public ResponseEntity<?> removeVideo(@PathVariable Long id, @PathVariable Long videoId, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        Optional<Playlist> opt = playlistRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getUserId().equals(userId)) return ResponseEntity.status(403).build();

        playlistVideoRepository.findByPlaylistIdAndVideoId(id, videoId)
                .ifPresent(playlistVideoRepository::delete);

        return ResponseEntity.ok(Map.of("success", true));
    }
}
