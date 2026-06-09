package com.example.demo.controller;

import com.example.demo.config.AdminChecker;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.S3StorageService;
import com.example.demo.entity.Subscription;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoSave;
import com.example.demo.entity.VideoHistory;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoHistoryRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class VideoController {

    private static final Set<String> ALLOWED_IMAGE_EXTS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024; // 20 MB

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final VideoHistoryRepository videoHistoryRepository;
    private final CommentRepository commentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final LoginUserResolver loginUserResolver;
    private final AdminChecker adminChecker;
    private final S3StorageService storageService;

    public VideoController(
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoSaveRepository videoSaveRepository,
            VideoHistoryRepository videoHistoryRepository,
            CommentRepository commentRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            LoginUserResolver loginUserResolver,
            AdminChecker adminChecker,
            S3StorageService storageService
    ) {
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.videoHistoryRepository = videoHistoryRepository;
        this.commentRepository = commentRepository;
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

        videos.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return toVideoItems(videos, loginUserId);
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
        List<VideoItem> items = toVideoItems(videoPage.getContent(), loginUserId);

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
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String sortBy,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        PageRequest pageable = PageRequest.of(page, size);

        Page<Video> videoPage;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean isPopular = "popular".equals(sort) || "popular".equals(sortBy);
        boolean isByViewCount = "latest".equals(sortBy);

        if (ownerId != null) {
            videoPage = videoRepository.findPublicByOwnerIdPageable(ownerId, pageable);
        } else if (isPopular && hasKeyword) {
            videoPage = videoRepository.searchPublicByKeywordPageableOrderByViewCount(keyword, pageable);
        } else if (isPopular) {
            videoPage = videoRepository.findAllPublicPageableOrderByViewCount(pageable);
        } else if (hasKeyword && hasCategory) {
            videoPage = videoRepository.searchPublicByKeywordAndCategoryPageable(keyword, category, pageable);
        } else if (hasKeyword) {
            videoPage = videoRepository.searchPublicByKeywordPageable(keyword, pageable);
        } else if (hasCategory) {
            videoPage = videoRepository.findAllPublicByCategoryPageable(category, pageable);
        } else {
            videoPage = videoRepository.findAllPublicPageable(pageable);
        }

        List<VideoItem> items = toVideoItems(videoPage.getContent(), loginUserId);

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
                        commentRepository.countByVideoIdAndParentIdIsNull(video.getId()),
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

        List<Video> videos = videoRepository.findByOwnerIdOrderByIdDesc(loginUserId);
        return ResponseEntity.ok(toVideoItems(videos, loginUserId));
    }

    @GetMapping("/studio/view-trend")
    public ResponseEntity<?> getViewTrend(
            @RequestParam(defaultValue = "28") int days,
            HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        if (days < 1 || days > 365) days = 28;

        List<Video> videos = videoRepository.findByOwnerIdOrderByIdDesc(loginUserId);
        if (videos.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> videoIds = videos.stream().map(Video::getId).collect(Collectors.toList());
        long since = System.currentTimeMillis() - (long) days * 86_400_000L;

        List<VideoHistory> histories = videoHistoryRepository.findByVideoIdInAndWatchedAtSince(videoIds, since);

        ZoneId zone = ZoneId.of("Asia/Seoul");
        Map<String, Long> countByDate = new TreeMap<>();

        LocalDate startDate = LocalDate.now(zone).minusDays(days - 1);
        for (int i = 0; i < days; i++) {
            countByDate.put(startDate.plusDays(i).toString(), 0L);
        }

        for (VideoHistory h : histories) {
            if (h.getWatchedAt() == null) continue;
            String dateStr = Instant.ofEpochMilli(h.getWatchedAt())
                    .atZone(zone).toLocalDate().toString();
            countByDate.merge(dateStr, 1L, Long::sum);
        }

        List<Map<String, Object>> result = countByDate.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-videos")
    public ResponseEntity<?> getMyVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<Video> videos = videoRepository.findByOwnerIdOrderByIdDesc(loginUserId);
        return ResponseEntity.ok(toVideoItems(videos, loginUserId));
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
                        commentRepository.countByVideoIdAndParentIdIsNull(saved.getId()),
                        videoLikeRepository.existsByVideoIdAndUserId(saved.getId(), loginUserId),
                        videoSaveRepository.existsByVideoIdAndUserId(saved.getId(), loginUserId)
                )
        );
    }

    @PostMapping("/videos/{id}/thumbnail")
    public ResponseEntity<?> replaceThumbnail(
            @PathVariable Long id,
            @RequestParam("thumbnailFile") MultipartFile thumbnailFile,
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

        if (thumbnailFile == null || thumbnailFile.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "썸네일 파일을 선택해줘."));
        }

        String ext = extensionOf(thumbnailFile.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "허용되지 않는 썸네일 형식입니다. (jpg, jpeg, png, gif, webp)"));
        }
        if (thumbnailFile.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "썸네일 파일 크기는 20MB를 초과할 수 없습니다."));
        }

        try {
            String newThumbnailUrl;
            if (storageService.isConfigured()) {
                newThumbnailUrl = uploadThumbnailToS3(thumbnailFile, ext);
            } else {
                String savedFileName = saveFile(thumbnailFile, thumbnailDir);
                newThumbnailUrl = "/uploads/thumbnails/" + savedFileName;
            }

            String oldThumbnailUrl = video.getThumbnail();
            video.setThumbnail(newThumbnailUrl);
            Video saved = videoRepository.save(video);

            deletePhysicalFile(oldThumbnailUrl);

            return ResponseEntity.ok(Map.of("success", true, "thumbnail", saved.getThumbnail()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new SimpleResponse(false, "썸네일 업로드 중 오류가 발생했어."));
        }
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

        List<Long> videoIds = videoLikeRepository.findByUserIdOrderByIdDesc(loginUserId)
                .stream()
                .map(VideoLike::getVideoId)
                .collect(Collectors.toList());

        if (videoIds.isEmpty()) return ResponseEntity.ok(List.of());

        boolean isAdminLiked = adminChecker.isAdmin(session, loginUserResolver);
        Map<Long, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        List<Video> videos = videoIds.stream()
                .map(videoMap::get)
                .filter(v -> v != null)
                .filter(v -> isAdminLiked || !"비공개".equals(v.getVisibility()) || loginUserId.equals(v.getOwnerId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(toVideoItems(videos, loginUserId));
    }

    @GetMapping("/my-saved-videos")
    public ResponseEntity<?> getMySavedVideos(HttpSession session) {
        Long loginUserId = getLoginUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<Long> videoIds = videoSaveRepository.findByUserIdOrderByIdDesc(loginUserId)
                .stream()
                .map(VideoSave::getVideoId)
                .collect(Collectors.toList());

        if (videoIds.isEmpty()) return ResponseEntity.ok(List.of());

        boolean isAdminSaved = adminChecker.isAdmin(session, loginUserResolver);
        Map<Long, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        List<Video> videos = videoIds.stream()
                .map(videoMap::get)
                .filter(v -> v != null)
                .filter(v -> isAdminSaved || !"비공개".equals(v.getVisibility()) || loginUserId.equals(v.getOwnerId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(toVideoItems(videos, loginUserId));
    }

    // N+1 방지: 영상 목록 전체에 대해 4번의 배치 쿼리로 통계를 한 번에 조회
    List<VideoItem> toVideoItems(List<Video> videos, Long loginUserId) {
        if (videos.isEmpty()) return Collections.emptyList();
        List<Long> ids = videos.stream().map(Video::getId).collect(Collectors.toList());

        Map<Long, Long> likeCounts = toCountMap(videoLikeRepository.countByVideoIdIn(ids));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByVideoIdIn(ids));
        Set<Long> likedSet = loginUserId != null
                ? new HashSet<>(videoLikeRepository.findLikedVideoIdsByUserId(loginUserId, ids))
                : Collections.emptySet();
        Set<Long> savedSet = loginUserId != null
                ? new HashSet<>(videoSaveRepository.findSavedVideoIdsByUserId(loginUserId, ids))
                : Collections.emptySet();

        return videos.stream()
                .map(v -> VideoItem.from(v,
                        likeCounts.getOrDefault(v.getId(), 0L),
                        commentCounts.getOrDefault(v.getId(), 0L),
                        likedSet.contains(v.getId()),
                        savedSet.contains(v.getId())))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private Long getLoginUserId(HttpSession session) {
        return loginUserResolver.getUserId(session);
    }

    private String saveFile(MultipartFile file, String dir) throws Exception {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf(".");
        if (lastDotIndex != -1) extension = originalFilename.substring(lastDotIndex);
        String savedFileName = UUID.randomUUID() + extension;
        Path savePath = Paths.get(dir).toAbsolutePath().resolve(savedFileName);
        file.transferTo(savePath);
        return savedFileName;
    }

    private String uploadThumbnailToS3(MultipartFile file, String ext) throws Exception {
        String uuid = UUID.randomUUID().toString();
        String key = "thumbnails/" + uuid + ext;
        Path temp = Files.createTempFile("thumb_" + uuid, ext);
        try {
            file.transferTo(temp);
            return storageService.upload(temp, key, contentTypeFor(ext));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String contentTypeFor(String ext) {
        return switch (ext.toLowerCase()) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
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
        private long commentCount;
        private boolean likedByMe;
        private boolean savedByMe;

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
    }
}
