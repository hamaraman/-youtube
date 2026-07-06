package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.Video;
import com.example.demo.service.VideoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final VideoService videoService;
    private final LoginUserResolver loginUserResolver;
    private final AdminChecker adminChecker;

    public VideoController(
            VideoService videoService,
            LoginUserResolver loginUserResolver,
            AdminChecker adminChecker
    ) {
        this.videoService = videoService;
        this.loginUserResolver = loginUserResolver;
        this.adminChecker = adminChecker;
    }

    @GetMapping("/videos")
    public List<VideoItem> getVideos(
            @RequestParam(required = false) String keyword,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        boolean isAdmin = adminChecker.isAdmin(session, loginUserResolver);
        return videoService.getVideos(keyword, loginUserId, isAdmin);
    }

    @GetMapping("/users/{id}/channel")
    public ResponseEntity<?> getChannelProfile(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        try {
            return ResponseEntity.ok(videoService.getChannelProfile(id, loginUserId));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @GetMapping("/videos/subscriptions")
    public Map<String, Object> getSubscriptionFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        return videoService.getSubscriptionFeed(loginUserId, page, size);
    }

    @GetMapping("/videos/categories")
    public List<String> getCategories() {
        return videoService.getCategories();
    }

    @GetMapping("/videos/feed")
    public Map<String, Object> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String sortBy,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        return videoService.getFeed(page, size, keyword, category, ownerId, sort, sortBy, loginUserId);
    }

    @GetMapping("/videos/{id}")
    public ResponseEntity<?> getVideoById(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        boolean isAdmin = adminChecker.isAdmin(session, loginUserResolver);
        try {
            return ResponseEntity.ok(videoService.getVideoById(id, loginUserId, isAdmin));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 403) {
                return ResponseEntity.status(403).body(new SimpleResponse(false, e.getReason()));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @GetMapping("/videos/{id}/related")
    public ResponseEntity<?> getRelatedVideos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "12") int limit,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        try {
            return ResponseEntity.ok(videoService.getRelatedVideos(id, loginUserId, limit));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @GetMapping("/studio/videos")
    public ResponseEntity<?> getStudioVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        try {
            return ResponseEntity.ok(videoService.getStudioVideos(loginUserId));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @GetMapping("/studio/view-trend")
    public ResponseEntity<?> getViewTrend(
            @RequestParam(defaultValue = "28") int days,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        try {
            return ResponseEntity.ok(videoService.getViewTrend(loginUserId, days));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @GetMapping("/my-videos")
    public ResponseEntity<?> getMyVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        return ResponseEntity.ok(videoService.getMyVideos(loginUserId));
    }

    @PutMapping("/videos/{id}")
    public ResponseEntity<?> updateVideo(
            @PathVariable Long id,
            @RequestBody VideoUpdateRequest request,
            HttpSession session
    ) {
        Long loginUserId = getLoginUserId(session);
        try {
            VideoItem updated = videoService.updateVideo(id, loginUserId, request);
            return ResponseEntity.ok(updated);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403 || e.getStatusCode().value() == 400) {
                return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @PostMapping("/videos/{id}/thumbnail")
    public ResponseEntity<?> replaceThumbnail(
            @PathVariable Long id,
            @RequestParam("thumbnailFile") MultipartFile thumbnailFile,
            HttpSession session
    ) {
        Long loginUserId = getLoginUserId(session);
        try {
            String newThumbnailUrl = videoService.replaceThumbnail(id, loginUserId, thumbnailFile);
            return ResponseEntity.ok(Map.of("success", true, "thumbnail", newThumbnailUrl));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403 || e.getStatusCode().value() == 400) {
                return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<?> deleteVideo(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        try {
            videoService.deleteVideo(id, loginUserId);
            return ResponseEntity.ok(new SimpleResponse(true, "영상이 삭제되었습니다."));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
            }
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @GetMapping("/my-liked-videos")
    public ResponseEntity<?> getMyLikedVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        boolean isAdmin = adminChecker.isAdmin(session, loginUserResolver);
        return ResponseEntity.ok(videoService.getMyLikedVideos(loginUserId, isAdmin));
    }

    @GetMapping("/my-saved-videos")
    public ResponseEntity<?> getMySavedVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        boolean isAdmin = adminChecker.isAdmin(session, loginUserResolver);
        return ResponseEntity.ok(videoService.getMySavedVideos(loginUserId, isAdmin));
    }

    private Long getLoginUserId(HttpSession session) {
        return loginUserResolver.getUserId(session);
    }

    public static class SimpleResponse {
        private boolean success;
        private String message;

        public SimpleResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class VideoUpdateRequest {
        private String title;
        private String description;
        private String thumbnail;
        private String embedUrl;
        private String channel;
        private String avatar;
        private String category;
        private String duration;
        private String visibility;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getThumbnail() { return thumbnail; }
        public String getEmbedUrl() { return embedUrl; }
        public String getChannel() { return channel; }
        public String getAvatar() { return avatar; }
        public String getCategory() { return category; }
        public String getDuration() { return duration; }
        public String getVisibility() { return visibility; }

        public void setTitle(String title) { this.title = title; }
        public void setDescription(String description) { this.description = description; }
        public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
        public void setEmbedUrl(String embedUrl) { this.embedUrl = embedUrl; }
        public void setChannel(String channel) { this.channel = channel; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public void setCategory(String category) { this.category = category; }
        public void setDuration(String duration) { this.duration = duration; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
    }

    public static class VideoItem {
        private Long id;
        private Long ownerId;
        private String title;
        private String channel;
        private String thumbnail;
        private String avatar;
        private String duration;
        private String category;
        private String description;
        private String date;
        private String videoUrl;
        private String videoUrl1080;
        private String videoUrl720;
        private String videoUrl480;
        private String videoUrl360;
        private String visibility;
        private String embedUrl;
        private long viewCount;
        private long likeCount;
        private long commentCount;
        private boolean likedByMe;
        private boolean savedByMe;
        private long dislikeCount;
        private boolean dislikedByMe;

        public static VideoItem from(Video video, long likeCount, long commentCount, boolean likedByMe, boolean savedByMe) {
            VideoItem item = new VideoItem();
            item.id = video.getId();
            item.ownerId = video.getOwnerId();
            item.title = video.getTitle();
            item.channel = video.getChannel();
            item.thumbnail = video.getThumbnail();
            item.avatar = video.getAvatar();
            item.duration = video.getDuration();
            item.category = video.getCategory();
            item.description = video.getDescription();
            item.date = video.getDateText();
            item.videoUrl = video.getVideoUrl();
            item.videoUrl1080 = video.getVideoUrl1080();
            item.videoUrl720 = video.getVideoUrl720();
            item.videoUrl480 = video.getVideoUrl480();
            item.videoUrl360 = video.getVideoUrl360();
            item.visibility = video.getVisibility();
            item.embedUrl = video.getEmbedUrl();
            item.viewCount = video.getViewCount();
            item.likeCount = likeCount;
            item.commentCount = commentCount;
            item.likedByMe = likedByMe;
            item.savedByMe = savedByMe;
            return item;
        }

        public Long getId() { return id; }
        public Long getOwnerId() { return ownerId; }
        public String getTitle() { return title; }
        public String getChannel() { return channel; }
        public String getThumbnail() { return thumbnail; }
        public String getAvatar() { return avatar; }
        public String getDuration() { return duration; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public String getDate() { return date; }
        public String getVideoUrl() { return videoUrl; }
        public String getVideoUrl1080() { return videoUrl1080; }
        public String getVideoUrl720() { return videoUrl720; }
        public String getVideoUrl480() { return videoUrl480; }
        public String getVideoUrl360() { return videoUrl360; }
        public String getVisibility() { return visibility; }
        public String getEmbedUrl() { return embedUrl; }
        public long getViewCount() { return viewCount; }
        public long getLikeCount() { return likeCount; }
        public long getCommentCount() { return commentCount; }
        public boolean isLikedByMe() { return likedByMe; }
        public boolean isSavedByMe() { return savedByMe; }
        public long getDislikeCount() { return dislikeCount; }
        public boolean isDislikedByMe() { return dislikedByMe; }
        public void setDislikeCount(long dislikeCount) { this.dislikeCount = dislikeCount; }
        public void setDislikedByMe(boolean dislikedByMe) { this.dislikedByMe = dislikedByMe; }
    }
}
