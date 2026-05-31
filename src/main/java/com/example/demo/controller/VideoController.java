package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.S3StorageService;
import com.example.demo.entity.Subscription;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoSave;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final LoginUserResolver loginUserResolver;
    private final AdminChecker adminChecker;
    private final S3StorageService storageService;

    public VideoController(
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoSaveRepository videoSaveRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            LoginUserResolver loginUserResolver,
            AdminChecker adminChecker,
            S3StorageService storageService
    ) {
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.loginUserResolver = loginUserResolver;
        this.adminChecker = adminChecker;
        this.storageService = storageService;
    }

    @GetMapping("/videos")
    public List<VideoItem> getVideos(
            @RequestParam(required = false) String keyword,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        boolean isAdmin = adminChecker.isAdmin(session, loginUserResolver);

        List<Video> videos;
        if (isAdmin) {
            videos = (keyword != null && !keyword.isBlank())
                    ? videoRepository.searchByKeyword(keyword)
                    : videoRepository.findAll();
        } else {
            videos = (keyword != null && !keyword.isBlank())
                    ? videoRepository.searchPublicByKeyword(keyword)
                    : videoRepository.findAllPublic();
        }

        return videos.stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(video -> VideoItem.from(
                        video,
                        videoLikeRepository.countByVideoId(video.getId()),
                        loginUserId != null && videoLikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId),
                        loginUserId != null && videoSaveRepository.existsByVideoIdAndUserId(video.getId(), loginUserId)
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/users/{id}/channel")
    public ResponseEntity<?> getChannelProfile(@PathVariable Long id, HttpSession session) {
        return userRepository.findById(id).map(user -> {
            Long loginUserId = getLoginUserId(session);
            Map<String, Object> result = new HashMap<>();
            result.put("id", user.getId());
            result.put("channelName", user.getChannelName() != null ? user.getChannelName() : user.getNickname());
            result.put("profileImage", user.getProfileImage());
            result.put("bannerImage", user.getBannerImage());
            result.put("bio", user.getBio());
            result.put("subscriberCount", subscriptionRepository.countByChannelOwnerId(id));
            result.put("videoCount", videoRepository.countPublicByOwnerId(id));
            result.put("subscribed", loginUserId != null &&
                    subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(loginUserId, id));
            result.put("isMe", loginUserId != null && loginUserId.equals(id));
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/videos/subscriptions")
    public Map<String, Object> getSubscriptionFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return Map.of("videos", List.of(), "hasMore", false, "page", 0, "totalElements", 0L);
        }

        List<Long> ownerIds = subscriptionRepository.findBySubscriberId(loginUserId)
                .stream()
                .map(Subscription::getChannelOwnerId)
                .collect(Collectors.toList());

        if (ownerIds.isEmpty()) {
            return Map.of("videos", List.of(), "hasMore", false, "page", 0, "totalElements", 0L);
        }

        Page<Video> videoPage = videoRepository.findByOwnerIdsPageable(ownerIds, PageRequest.of(page, size));
        List<VideoItem> items = videoPage.getContent().stream()
                .map(v -> VideoItem.from(
                        v,
                        videoLikeRepository.countByVideoId(v.getId()),
                        videoLikeRepository.existsByVideoIdAndUserId(v.getId(), loginUserId),
                        videoSaveRepository.existsByVideoIdAndUserId(v.getId(), loginUserId)
                ))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("videos", items);
        result.put("hasMore", !videoPage.isLast());
        result.put("page", page);
        result.put("totalElements", videoPage.getTotalElements());
        return result;
    }

    @GetMapping("/videos/categories")
    public List<String> getCategories() {
        return videoRepository.findAllPublicCategories();
    }

    @GetMapping("/videos/feed")
    public Map<String, Object> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long ownerId,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        PageRequest pageable = PageRequest.of(page, size);

        Page<Video> videoPage;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = category != null && !category.isBlank();

        if (ownerId != null) {
            videoPage = videoRepository.findPublicByOwnerIdPageable(ownerId, pageable);
        } else if (hasKeyword && hasCategory) {
            videoPage = videoRepository.searchPublicByKeywordAndCategoryPageable(keyword, category, pageable);
        } else if (hasKeyword) {
            videoPage = videoRepository.searchPublicByKeywordPageable(keyword, pageable);
        } else if (hasCategory) {
            videoPage = videoRepository.findAllPublicByCategoryPageable(category, pageable);
        } else {
            videoPage = videoRepository.findAllPublicPageable(pageable);
        }

        List<VideoItem> items = videoPage.getContent().stream()
                .map(v -> VideoItem.from(
                        v,
                        videoLikeRepository.countByVideoId(v.getId()),
                        loginUserId != null && videoLikeRepository.existsByVideoIdAndUserId(v.getId(), loginUserId),
                        loginUserId != null && videoSaveRepository.existsByVideoIdAndUserId(v.getId(), loginUserId)
                ))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("videos", items);
        result.put("hasMore", !videoPage.isLast());
        result.put("page", page);
        result.put("totalElements", videoPage.getTotalElements());
        return result;
    }

    @GetMapping("/videos/{id}")
    public ResponseEntity<?> getVideoById(@PathVariable Long id, HttpSession session) {
        Optional<Video> optionalVideo = videoRepository.findById(id);

        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Long loginUserId = getLoginUserId(session);
        Video video = optionalVideo.get();

        if ("비공개".equals(video.getVisibility())) {
            boolean isAdmin = adminChecker.isAdmin(session, loginUserResolver);
            if (!isAdmin && (loginUserId == null || !loginUserId.equals(video.getOwnerId()))) {
                return ResponseEntity.status(403).body(new SimpleResponse(false, "비공개 영상입니다."));
            }
        }

        return ResponseEntity.ok(
                VideoItem.from(
                        video,
                        videoLikeRepository.countByVideoId(video.getId()),
                        loginUserId != null && videoLikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId),
                        loginUserId != null && videoSaveRepository.existsByVideoIdAndUserId(video.getId(), loginUserId)
                )
        );
    }

    @GetMapping("/studio/videos")
    public ResponseEntity<?> getStudioVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        List<VideoItem> result = videoRepository.findAll()
                .stream()
                .filter(video -> loginUserId.equals(video.getOwnerId()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(video -> VideoItem.from(
                        video,
                        videoLikeRepository.countByVideoId(video.getId()),
                        videoLikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId),
                        videoSaveRepository.existsByVideoIdAndUserId(video.getId(), loginUserId)
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-videos")
    public ResponseEntity<?> getMyVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<VideoItem> result = videoRepository.findAll()
                .stream()
                .filter(video -> loginUserId.equals(video.getOwnerId()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(video -> VideoItem.from(
                        video,
                        videoLikeRepository.countByVideoId(video.getId()),
                        videoLikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId),
                        videoSaveRepository.existsByVideoIdAndUserId(video.getId(), loginUserId)
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/videos/{id}")
    public ResponseEntity<?> updateVideo(
            @PathVariable Long id,
            @RequestBody VideoUpdateRequest request,
            HttpSession session
    ) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Video video = optionalVideo.get();

        if (video.getOwnerId() == null || !loginUserId.equals(video.getOwnerId())) {
            return ResponseEntity.status(403).body(new SimpleResponse(false, "본인 영상만 수정할 수 있습니다."));
        }

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "제목을 입력해줘."));
        }

        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "설명을 입력해줘."));
        }

        if (request.getChannel() == null || request.getChannel().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "채널명을 입력해줘."));
        }

        if (request.getDuration() == null || request.getDuration().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "영상 길이를 입력해줘."));
        }

        video.setTitle(request.getTitle().trim());
        video.setDescription(request.getDescription().trim());
        video.setThumbnail(request.getThumbnail() == null ? "" : request.getThumbnail().trim());
        video.setEmbedUrl(request.getEmbedUrl() == null ? "" : request.getEmbedUrl().trim());
        video.setChannel(request.getChannel().trim());
        video.setAvatar(request.getAvatar() == null ? "" : request.getAvatar().trim());
        video.setCategory(
                request.getCategory() == null || request.getCategory().trim().isEmpty()
                        ? "기타"
                        : request.getCategory().trim()
        );
        video.setDuration(request.getDuration().trim());
        video.setVisibility(
                request.getVisibility() == null || request.getVisibility().trim().isEmpty()
                        ? "공개"
                        : request.getVisibility().trim()
        );

        Video saved = videoRepository.save(video);

        return ResponseEntity.ok(
                VideoItem.from(
                        saved,
                        videoLikeRepository.countByVideoId(saved.getId()),
                        videoLikeRepository.existsByVideoIdAndUserId(saved.getId(), loginUserId),
                        videoSaveRepository.existsByVideoIdAndUserId(saved.getId(), loginUserId)
                )
        );
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<?> deleteVideo(@PathVariable Long id, HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Video video = optionalVideo.get();

        if (video.getOwnerId() == null || !loginUserId.equals(video.getOwnerId())) {
            return ResponseEntity.status(403).body(new SimpleResponse(false, "본인 영상만 삭제할 수 있습니다."));
        }

        deletePhysicalFile(video.getVideoUrl());
        deletePhysicalFile(video.getVideoUrl1080());
        deletePhysicalFile(video.getVideoUrl720());
        deletePhysicalFile(video.getVideoUrl480());
        deletePhysicalFile(video.getVideoUrl360());
        deletePhysicalFile(video.getThumbnail());

        videoRepository.delete(video);

        return ResponseEntity.ok(new SimpleResponse(true, "영상이 삭제되었습니다."));
    }

    @GetMapping("/my-liked-videos")
    public ResponseEntity<?> getMyLikedVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<VideoLike> likes = videoLikeRepository.findByUserIdOrderByIdDesc(loginUserId);

        boolean isAdminLiked = adminChecker.isAdmin(session, loginUserResolver);
        List<VideoItem> result = likes.stream()
                .map(VideoLike::getVideoId)
                .map(videoRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(video -> isAdminLiked || !"비공개".equals(video.getVisibility()) || loginUserId.equals(video.getOwnerId()))
                .map(video -> VideoItem.from(
                        video,
                        videoLikeRepository.countByVideoId(video.getId()),
                        true,
                        videoSaveRepository.existsByVideoIdAndUserId(video.getId(), loginUserId)
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-saved-videos")
    public ResponseEntity<?> getMySavedVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<VideoSave> saves = videoSaveRepository.findByUserIdOrderByIdDesc(loginUserId);

        boolean isAdminSaved = adminChecker.isAdmin(session, loginUserResolver);
        List<VideoItem> result = saves.stream()
                .map(VideoSave::getVideoId)
                .map(videoRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(video -> isAdminSaved || !"비공개".equals(video.getVisibility()) || loginUserId.equals(video.getOwnerId()))
                .map(video -> VideoItem.from(
                        video,
                        videoLikeRepository.countByVideoId(video.getId()),
                        videoLikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId),
                        true
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private Long getLoginUserId(HttpSession session) {
        return loginUserResolver.getUserId(session);
    }

    private void deletePhysicalFile(String url) {
        try {
            if (url == null || url.isBlank()) return;
            if (url.startsWith("http")) {
                storageService.delete(url);
            } else if (url.startsWith("/uploads/")) {
                File file = new File(url.substring(1));
                if (file.exists()) file.delete();
            }
        } catch (Exception ignored) {}
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
        private boolean likedByMe;
        private boolean savedByMe;

        public static VideoItem from(Video video, long likeCount, boolean likedByMe, boolean savedByMe) {
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
        public boolean isLikedByMe() { return likedByMe; }
        public boolean isSavedByMe() { return savedByMe; }
    }
}