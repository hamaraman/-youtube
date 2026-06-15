package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.PlaylistService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final LoginUserResolver loginUserResolver;

    public PlaylistController(PlaylistService playlistService,
                              LoginUserResolver loginUserResolver) {
        this.playlistService = playlistService;
        this.loginUserResolver = loginUserResolver;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPlaylists(@PathVariable Long userId) {
        return ResponseEntity.ok(playlistService.getUserPlaylists(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyPlaylists(HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(playlistService.getMyPlaylists(userId));
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        String name = body.getOrDefault("name", "");
        try {
            return ResponseEntity.ok(playlistService.createPlaylist(name, userId));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("success", false, "message", e.getReason()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        try {
            playlistService.deletePlaylist(id, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @GetMapping("/{id}/videos")
    public ResponseEntity<?> getPlaylistVideos(@PathVariable Long id, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        try {
            Map<String, Object> details = playlistService.getPlaylistDetails(id, userId);
            return ResponseEntity.ok(details);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> renamePlaylist(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        String name = body.getOrDefault("name", "");
        try {
            playlistService.renamePlaylist(id, name, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 400) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getReason()));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @PostMapping("/{id}/videos")
    public ResponseEntity<?> addVideo(@PathVariable Long id, @RequestBody Map<String, Long> body, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        Long videoId = body.get("videoId");
        try {
            Map<String, Object> result = playlistService.addVideo(id, videoId, userId);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 400) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getReason()));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @DeleteMapping("/{id}/videos/{videoId}")
    public ResponseEntity<?> removeVideo(@PathVariable Long id, @PathVariable Long videoId, HttpSession session) {
        Long userId = loginUserResolver.getUserId(session);
        if (userId == null) return ResponseEntity.status(401).build();

        try {
            playlistService.removeVideo(id, videoId, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }
}
