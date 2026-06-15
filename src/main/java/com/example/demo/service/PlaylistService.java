package com.example.demo.service;

import com.example.demo.entity.Playlist;
import com.example.demo.entity.PlaylistVideo;
import com.example.demo.entity.Video;
import com.example.demo.repository.PlaylistRepository;
import com.example.demo.repository.PlaylistVideoRepository;
import com.example.demo.repository.VideoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistVideoRepository playlistVideoRepository;
    private final VideoRepository videoRepository;

    public PlaylistService(PlaylistRepository playlistRepository,
                           PlaylistVideoRepository playlistVideoRepository,
                           VideoRepository videoRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistVideoRepository = playlistVideoRepository;
        this.videoRepository = videoRepository;
    }

    public List<Map<String, Object>> getUserPlaylists(Long userId) {
        List<Playlist> playlists = playlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return playlists.stream().map(p -> {
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
    }

    public List<Map<String, Object>> getMyPlaylists(Long userId) {
        return getUserPlaylists(userId);
    }

    public Map<String, Object> createPlaylist(String name, Long userId) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "재생목록 이름을 입력해줘.");
        }

        Playlist p = new Playlist();
        p.setUserId(userId);
        p.setName(trimmedName);
        playlistRepository.save(p);

        return Map.of("success", true, "id", p.getId(), "name", p.getName());
    }

    public void deletePlaylist(Long id, Long userId) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "재생목록을 찾을 수 없습니다."));

        if (!playlist.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }

        playlistVideoRepository.deleteByPlaylistId(id);
        playlistRepository.delete(playlist);
    }

    public Map<String, Object> getPlaylistDetails(Long id, Long userId) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "재생목록을 찾을 수 없습니다."));

        boolean isOwner = userId != null && playlist.getUserId().equals(userId);

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

        return Map.of("name", playlist.getName(), "videos", videos, "isOwner", isOwner);
    }

    public void renamePlaylist(Long id, String name, Long userId) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "재생목록을 찾을 수 없습니다."));

        if (!playlist.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름을 입력해줘.");
        }

        playlist.setName(trimmedName);
        playlistRepository.save(playlist);
    }

    public Map<String, Object> addVideo(Long id, Long videoId, Long userId) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "재생목록을 찾을 수 없습니다."));

        if (!playlist.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }

        if (videoId == null || videoRepository.findById(videoId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "영상을 찾을 수 없어.");
        }

        if (playlistVideoRepository.existsByPlaylistIdAndVideoId(id, videoId)) {
            return Map.of("success", false, "message", "이미 추가된 영상이야.");
        }

        PlaylistVideo pv = new PlaylistVideo();
        pv.setPlaylistId(id);
        pv.setVideoId(videoId);
        playlistVideoRepository.save(pv);

        return Map.of("success", true);
    }

    public void removeVideo(Long id, Long videoId, Long userId) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "재생목록을 찾을 수 없습니다."));

        if (!playlist.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }

        playlistVideoRepository.findByPlaylistIdAndVideoId(id, videoId)
                .ifPresent(playlistVideoRepository::delete);
    }
}
