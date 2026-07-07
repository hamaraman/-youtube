package com.example.demo.service;

import com.example.demo.controller.VideoController.VideoItem;
import com.example.demo.controller.VideoController.VideoUpdateRequest;
import com.example.demo.entity.Subscription;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoHistory;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoSave;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoService {

    private static final Set<String> ALLOWED_IMAGE_EXTS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024; // 20 MB

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final VideoDislikeRepository videoDislikeRepository;
    private final VideoSaveRepository videoSaveRepository;
    private final VideoHistoryRepository videoHistoryRepository;
    private final CommentRepository commentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final S3StorageService storageService;

    public VideoService(
            VideoRepository videoRepository,
            VideoLikeRepository videoLikeRepository,
            VideoDislikeRepository videoDislikeRepository,
            VideoSaveRepository videoSaveRepository,
            VideoHistoryRepository videoHistoryRepository,
            CommentRepository commentRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            S3StorageService storageService
    ) {
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.videoDislikeRepository = videoDislikeRepository;
        this.videoSaveRepository = videoSaveRepository;
        this.videoHistoryRepository = videoHistoryRepository;
        this.commentRepository = commentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    public List<VideoItem> getVideos(String keyword, Long loginUserId, boolean isAdmin) {
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

    public Map<String, Object> getChannelProfile(Long channelOwnerId, Long loginUserId) {
        return userRepository.findById(channelOwnerId).map(user -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", user.getId());
            result.put("channelName", user.getChannelName() != null ? user.getChannelName() : user.getNickname());
            result.put("profileImage", user.getProfileImage());
            result.put("bannerImage", user.getBannerImage());
            result.put("bio", user.getBio());
            result.put("subscriberCount", subscriptionRepository.countByChannelOwnerId(channelOwnerId));
            result.put("videoCount", videoRepository.countPublicByOwnerId(channelOwnerId));
            result.put("subscribed", loginUserId != null &&
                    subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(loginUserId, channelOwnerId));
            result.put("isMe", loginUserId != null && loginUserId.equals(channelOwnerId));
            return result;
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채널을 찾을 수 없습니다."));
    }

    public Map<String, Object> getSubscriptionFeed(Long loginUserId, int page, int size) {
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

    public List<String> getCategories() {
        return videoRepository.findAllPublicCategories();
    }

    public Map<String, Object> getFeed(int page, int size, String keyword, String category, Long ownerId, String sort, String sortBy, Long loginUserId) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<Video> videoPage;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean isPopular = "popular".equals(sort) || "popular".equals(sortBy);

        // 홈 기본 피드(로그인 + 검색어·카테고리·채널·인기정렬 아님)는 개인화 랭킹으로 제공한다.
        // 신호가 하나도 없거나 후보가 비면 null을 받아 아래 기본(최신순) 로직으로 폴백한다.
        boolean isHomeDefault = loginUserId != null && !hasKeyword && !hasCategory
                && ownerId == null && !isPopular;
        if (isHomeDefault) {
            Map<String, Object> personalized = getPersonalizedFeed(page, size, loginUserId);
            if (personalized != null) return personalized;
        }

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

    /**
     * 개인화 홈 피드. 로그인 유저의 좋아요·시청기록·구독 신호로 카테고리/채널 선호도를 만들고
     * 공개 영상 전체를 점수화해 정렬한다. 신호가 하나도 없거나 후보가 비면 null을 반환해
     * 호출부가 기본 최신순 피드로 폴백하게 한다.
     *
     * 점수 = 구독 채널 부스트 + 카테고리 선호 + 채널 선호 + 최신성(지수 감쇠)
     *        + 인기(조회수 로그) − 이미 본 영상 감점 + (user,video) 고정 노이즈.
     *
     * 소규모 카탈로그를 가정하고 공개 영상을 메모리에서 랭킹한다. 카탈로그가 커지면
     * 후보를 SQL에서 1차로 좁히거나 세션 스냅샷 캐시가 필요하다.
     */
    private Map<String, Object> getPersonalizedFeed(int page, int size, Long userId) {
        List<VideoLike> likes = videoLikeRepository.findByUserIdOrderByIdDesc(userId);
        List<VideoHistory> history = videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(userId);
        Set<Long> subscribedOwnerIds = subscriptionRepository.findBySubscriberId(userId).stream()
                .map(Subscription::getChannelOwnerId)
                .collect(Collectors.toSet());

        // 신호가 전혀 없으면 개인화 근거가 없다 → null 반환해 기본 최신순으로 폴백
        if (likes.isEmpty() && history.isEmpty() && subscribedOwnerIds.isEmpty()) {
            return null;
        }

        Set<Long> watchedVideoIds = history.stream()
                .map(VideoHistory::getVideoId)
                .collect(Collectors.toSet());

        // 좋아요(강한 신호)·시청기록(중간 신호)한 영상들의 카테고리/채널 선호도 집계
        Map<String, Double> categoryAffinity = new HashMap<>();
        Map<Long, Double> ownerAffinity = new HashMap<>();
        for (Video v : videoRepository.findAllById(
                likes.stream().map(VideoLike::getVideoId).collect(Collectors.toList()))) {
            accumulateAffinity(categoryAffinity, ownerAffinity, v, 3.0);
        }
        for (Video v : videoRepository.findAllById(new ArrayList<>(watchedVideoIds))) {
            accumulateAffinity(categoryAffinity, ownerAffinity, v, 1.0);
        }

        // 후보: 공개 영상 전체에서 본인 업로드는 제외 (자기 영상은 홈 추천에 안 띄운다)
        List<Video> candidates = videoRepository.findAllPublic().stream()
                .filter(v -> !userId.equals(v.getOwnerId()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Double> scores = new HashMap<>();
        for (Video v : candidates) {
            scores.put(v.getId(), personalizedScore(
                    v, categoryAffinity, ownerAffinity, subscribedOwnerIds, watchedVideoIds, now, userId));
        }
        candidates.sort(Comparator
                .comparingDouble((Video v) -> scores.get(v.getId())).reversed()
                .thenComparing(Comparator.comparingLong(Video::getId).reversed()));

        int total = candidates.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<VideoItem> items = toVideoItems(candidates.subList(from, to), userId);

        Map<String, Object> result = new HashMap<>();
        result.put("videos", items);
        result.put("hasMore", to < total);
        result.put("page", page);
        result.put("totalElements", (long) total);
        return result;
    }

    private void accumulateAffinity(Map<String, Double> categoryAffinity,
                                    Map<Long, Double> ownerAffinity, Video v, double weight) {
        String category = trimmed(v.getCategory());
        if (!category.isEmpty()) categoryAffinity.merge(category, weight, Double::sum);
        if (v.getOwnerId() != null) ownerAffinity.merge(v.getOwnerId(), weight, Double::sum);
    }

    private double personalizedScore(Video v, Map<String, Double> categoryAffinity,
                                     Map<Long, Double> ownerAffinity, Set<Long> subscribedOwnerIds,
                                     Set<Long> watchedVideoIds, LocalDateTime now, Long userId) {
        double score = 0;

        // 구독 채널: 가장 강한 신호
        if (v.getOwnerId() != null && subscribedOwnerIds.contains(v.getOwnerId())) {
            score += 40;
        }
        // 카테고리 선호도 (한 카테고리가 독식하지 않도록 상한을 둔다)
        score += Math.min(categoryAffinity.getOrDefault(trimmed(v.getCategory()), 0.0), 10.0) * 6;
        // 채널 선호도
        if (v.getOwnerId() != null) {
            score += Math.min(ownerAffinity.getOrDefault(v.getOwnerId(), 0.0), 10.0) * 4;
        }
        // 최신성: createdAt 기준 지수 감쇠 (반감기 약 14일)
        if (v.getCreatedAt() != null) {
            long days = Math.max(0, Duration.between(v.getCreatedAt(), now).toDays());
            score += 20 * Math.exp(-days / 14.0);
        }
        // 인기 프라이어: 조회수 로그 (약하게 — 신호가 적은 영상도 바닥 점수를 확보)
        if (v.getViewCount() > 0) {
            score += Math.log10(v.getViewCount() + 1) * 6;
        }
        // 이미 본 영상은 크게 감점하되 완전 제외는 하지 않는다(소규모 카탈로그에서 피드가 비는 것 방지)
        if (watchedVideoIds.contains(v.getId())) {
            score -= 60;
        }
        // 무한 스크롤 페이지네이션이 흔들리지 않도록 (user,video)에 고정된 노이즈를 쓴다
        score += new Random(userId * 1_000_003L + v.getId()).nextDouble() * 4;

        return score;
    }

    public VideoItem getVideoById(Long id, Long loginUserId, boolean isAdmin) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        if ("비공개".equals(video.getVisibility())) {
            if (!isAdmin && (loginUserId == null || !loginUserId.equals(video.getOwnerId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비공개 영상입니다.");
            }
        }

        VideoItem item = VideoItem.from(
                video,
                videoLikeRepository.countByVideoId(video.getId()),
                commentRepository.countByVideoIdAndParentIdIsNull(video.getId()),
                loginUserId != null && videoLikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId),
                loginUserId != null && videoSaveRepository.existsByVideoIdAndUserId(video.getId(), loginUserId)
        );
        item.setDislikeCount(videoDislikeRepository.countByVideoId(video.getId()));
        item.setDislikedByMe(loginUserId != null && videoDislikeRepository.existsByVideoIdAndUserId(video.getId(), loginUserId));
        return item;
    }

    /**
     * 시청 페이지 추천/같은 채널 목록. 프론트가 전체 영상 목록을 내려받아
     * 클라이언트에서 계산하던 것을 서버로 이관 (점수 로직은 기존과 동일:
     * 카테고리 +50, 같은 채널 +25, 키워드 겹침 +5/개, 조회수·좋아요 로그 보너스, 다양성 노이즈).
     */
    public Map<String, Object> getRelatedVideos(Long id, Long loginUserId, int limit) {
        Video base = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        int size = Math.max(1, Math.min(limit, 50));

        // 후보 풀을 SQL에서 1차로 좁힌다: 같은 채널 + 같은 카테고리 인기순 + 인기순 폴백.
        // 전체 공개 영상을 통째로 로드하지 않고 최대 poolCap 건만 점수 계산 대상으로 삼는다.
        int poolCap = size * 4;
        PageRequest categoryPage = PageRequest.of(0, poolCap);
        PageRequest popularPage = PageRequest.of(0, poolCap);
        LinkedHashMap<Long, Video> pool = new LinkedHashMap<>();

        // 같은 채널(주인) 영상 — 채널 목록과 추천 후보 양쪽에 쓴다
        if (base.getOwnerId() != null) {
            for (Video v : videoRepository.findRelatedByOwner(base.getOwnerId(), id, PageRequest.of(0, size))) {
                pool.putIfAbsent(v.getId(), v);
            }
        }

        // 같은 카테고리 인기순
        String baseCategory = trimmed(base.getCategory());
        if (!baseCategory.isEmpty()) {
            for (Video v : videoRepository.findRelatedByCategory(baseCategory, id, categoryPage)) {
                pool.putIfAbsent(v.getId(), v);
            }
        }

        // 카테고리/채널이 부족할 때 풀을 채우는 인기순 폴백
        for (Video v : videoRepository.findPopularPublicExcluding(id, popularPage)) {
            pool.putIfAbsent(v.getId(), v);
        }

        List<Video> candidates = new ArrayList<>(pool.values());
        List<VideoItem> items = toVideoItems(candidates, loginUserId);

        List<VideoItem> channelItems = items.stream()
                .filter(v -> base.getOwnerId() != null && base.getOwnerId().equals(v.getOwnerId()))
                .limit(size)
                .collect(Collectors.toList());

        Set<String> baseTokens = new HashSet<>();
        baseTokens.addAll(tokenize(base.getTitle()));
        baseTokens.addAll(tokenize(base.getDescription()));
        baseTokens.addAll(tokenize(base.getCategory()));

        Random noise = new Random();
        Map<Long, Double> scores = items.stream().collect(Collectors.toMap(
                VideoItem::getId,
                item -> recommendationScore(base, baseTokens, item, noise)));

        List<VideoItem> recommended = items.stream()
                .sorted(Comparator.comparingDouble((VideoItem v) -> scores.get(v.getId())).reversed()
                        .thenComparing(Comparator.comparingLong(VideoItem::getViewCount).reversed()))
                .limit(size)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("recommended", recommended);
        result.put("channel", channelItems);
        return result;
    }

    private double recommendationScore(Video base, Set<String> baseTokens, VideoItem target, Random noise) {
        double score = 0;

        String baseCategory = trimmed(base.getCategory());
        String targetCategory = trimmed(target.getCategory());
        if (!baseCategory.isEmpty() && baseCategory.equals(targetCategory)) score += 50;

        String baseChannel = trimmed(base.getChannel());
        if (!baseChannel.isEmpty() && baseChannel.equals(trimmed(target.getChannel()))) score += 25;

        List<String> targetTokens = new ArrayList<>();
        targetTokens.addAll(tokenize(target.getTitle()));
        targetTokens.addAll(tokenize(target.getDescription()));
        targetTokens.addAll(tokenize(target.getCategory()));
        for (String token : targetTokens) {
            if (baseTokens.contains(token)) score += 5;
        }

        if (target.getViewCount() > 0) score += Math.log10(target.getViewCount() + 1) * 10;
        if (target.getLikeCount() > 0) score += Math.log10(target.getLikeCount() + 1) * 5;

        // 매 요청 다른 추천 순서를 위한 다양성 노이즈 (기존 프론트 로직의 ±3점과 동일 스케일)
        score += noise.nextDouble() * 3;

        return score;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-z0-9가-힣\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toList());
    }

    public List<VideoItem> getStudioVideos(Long loginUserId) {
        if (loginUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        List<Video> videos = videoRepository.findByOwnerIdOrderByIdDesc(loginUserId);
        return toVideoItems(videos, loginUserId);
    }

    public List<Map<String, Object>> getViewTrend(Long loginUserId, int days) {
        if (loginUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        int trendDays = (days < 1 || days > 365) ? 28 : days;

        List<Video> videos = videoRepository.findByOwnerIdOrderByIdDesc(loginUserId);
        if (videos.isEmpty()) {
            return List.of();
        }

        List<Long> videoIds = videos.stream().map(Video::getId).collect(Collectors.toList());
        long since = System.currentTimeMillis() - (long) trendDays * 86_400_000L;

        List<VideoHistory> histories = videoHistoryRepository.findByVideoIdInAndWatchedAtSince(videoIds, since);

        ZoneId zone = ZoneId.of("Asia/Seoul");
        Map<String, Long> countByDate = new TreeMap<>();

        LocalDate startDate = LocalDate.now(zone).minusDays(trendDays - 1);
        for (int i = 0; i < trendDays; i++) {
            countByDate.put(startDate.plusDays(i).toString(), 0L);
        }

        for (VideoHistory h : histories) {
            if (h.getWatchedAt() == null) continue;
            String dateStr = Instant.ofEpochMilli(h.getWatchedAt())
                    .atZone(zone).toLocalDate().toString();
            countByDate.merge(dateStr, 1L, Long::sum);
        }

        return countByDate.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public List<VideoItem> getMyVideos(Long loginUserId) {
        if (loginUserId == null) {
            return List.of();
        }

        List<Video> videos = videoRepository.findByOwnerIdOrderByIdDesc(loginUserId);
        return toVideoItems(videos, loginUserId);
    }

    public VideoItem updateVideo(Long id, Long loginUserId, VideoUpdateRequest request) {
        if (loginUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        if (video.getOwnerId() == null || !loginUserId.equals(video.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 영상만 수정할 수 있습니다.");
        }

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해줘.");
        }

        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "설명을 입력해줘.");
        }

        if (request.getChannel() == null || request.getChannel().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "채널명을 입력해줘.");
        }

        if (request.getDuration() == null || request.getDuration().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "영상 길이를 입력해줘.");
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

        return VideoItem.from(
                saved,
                videoLikeRepository.countByVideoId(saved.getId()),
                commentRepository.countByVideoIdAndParentIdIsNull(saved.getId()),
                videoLikeRepository.existsByVideoIdAndUserId(saved.getId(), loginUserId),
                videoSaveRepository.existsByVideoIdAndUserId(saved.getId(), loginUserId)
        );
    }

    public String replaceThumbnail(Long id, Long loginUserId, MultipartFile thumbnailFile) {
        if (loginUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        if (video.getOwnerId() == null || !loginUserId.equals(video.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 영상만 수정할 수 있습니다.");
        }

        if (thumbnailFile == null || thumbnailFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "썸네일 파일을 선택해줘.");
        }

        String ext = extensionOf(thumbnailFile.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 썸네일 형식입니다. (jpg, jpeg, png, gif, webp)");
        }
        if (thumbnailFile.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "썸네일 파일 크기는 20MB를 초과할 수 없습니다.");
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

            return saved.getThumbnail();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "썸네일 업로드 중 오류가 발생했어.");
        }
    }

    public void deleteVideo(Long id, Long loginUserId) {
        if (loginUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        if (video.getOwnerId() == null || !loginUserId.equals(video.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 영상만 삭제할 수 있습니다.");
        }

        deletePhysicalFile(video.getVideoUrl());
        deletePhysicalFile(video.getVideoUrl1080());
        deletePhysicalFile(video.getVideoUrl720());
        deletePhysicalFile(video.getVideoUrl480());
        deletePhysicalFile(video.getVideoUrl360());
        deletePhysicalFile(video.getThumbnail());

        videoRepository.delete(video);
    }

    public List<VideoItem> getMyLikedVideos(Long loginUserId, boolean isAdmin) {
        if (loginUserId == null) {
            return List.of();
        }

        List<Long> videoIds = videoLikeRepository.findByUserIdOrderByIdDesc(loginUserId)
                .stream()
                .map(VideoLike::getVideoId)
                .collect(Collectors.toList());

        if (videoIds.isEmpty()) return List.of();

        Map<Long, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        List<Video> videos = videoIds.stream()
                .map(videoMap::get)
                .filter(v -> v != null)
                .filter(v -> isAdmin || !"비공개".equals(v.getVisibility()) || loginUserId.equals(v.getOwnerId()))
                .collect(Collectors.toList());

        return toVideoItems(videos, loginUserId);
    }

    public List<VideoItem> getMySavedVideos(Long loginUserId, boolean isAdmin) {
        if (loginUserId == null) {
            return List.of();
        }

        List<Long> videoIds = videoSaveRepository.findByUserIdOrderByIdDesc(loginUserId)
                .stream()
                .map(VideoSave::getVideoId)
                .collect(Collectors.toList());

        if (videoIds.isEmpty()) return List.of();

        Map<Long, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        List<Video> videos = videoIds.stream()
                .map(videoMap::get)
                .filter(v -> v != null)
                .filter(v -> isAdmin || !"비공개".equals(v.getVisibility()) || loginUserId.equals(v.getOwnerId()))
                .collect(Collectors.toList());

        return toVideoItems(videos, loginUserId);
    }

    public List<VideoItem> toVideoItems(List<Video> videos, Long loginUserId) {
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
}
