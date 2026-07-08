package com.example.demo.service;

import com.example.demo.controller.VideoController.VideoItem;
import com.example.demo.controller.VideoController.VideoUpdateRequest;
import com.example.demo.entity.Subscription;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoHistory;
import com.example.demo.entity.VideoLike;
import com.example.demo.entity.VideoSave;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoHistoryRepository;
import com.example.demo.repository.VideoDislikeRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import com.example.demo.repository.VideoSaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock private VideoRepository videoRepository;
    @Mock private VideoLikeRepository videoLikeRepository;
    @Mock private VideoDislikeRepository videoDislikeRepository;
    @Mock private VideoSaveRepository videoSaveRepository;
    @Mock private VideoHistoryRepository videoHistoryRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private S3StorageService storageService;

    @InjectMocks private VideoService videoService;

    private Video publicVideo(Long id, Long ownerId) {
        Video v = new Video();
        v.setId(id); v.setOwnerId(ownerId);
        v.setTitle("t" + id); v.setChannel("ch"); v.setThumbnail("th"); v.setDuration("1:00");
        v.setVisibility("공개");
        return v;
    }

    private Video privateVideo(Long id, Long ownerId) {
        Video v = publicVideo(id, ownerId);
        v.setVisibility("비공개");
        return v;
    }

    private void stubEmptyAggregates() {
        when(videoLikeRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
        when(commentRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
    }

    @Nested
    class GetVideos {

        @Test
        void admin_noKeyword_returnsAll() {
            when(videoRepository.findAll()).thenReturn(new ArrayList<>(List.of(publicVideo(1L, 5L), privateVideo(2L, 6L))));
            stubEmptyAggregates();

            List<VideoItem> result = videoService.getVideos(null, null, true);

            assertThat(result).hasSize(2);
            verify(videoRepository).findAll();
            verify(videoRepository, never()).findAllPublic();
        }

        @Test
        void nonAdmin_noKeyword_returnsPublic() {
            when(videoRepository.findAllPublic()).thenReturn(new ArrayList<>(List.of(publicVideo(1L, 5L))));
            stubEmptyAggregates();

            videoService.getVideos(null, null, false);

            verify(videoRepository).findAllPublic();
            verify(videoRepository, never()).findAll();
        }

        @Test
        void nonAdmin_withKeyword_usesPublicSearch() {
            when(videoRepository.searchPublicByKeyword("kw")).thenReturn(new ArrayList<>(List.of(publicVideo(1L, 5L))));
            stubEmptyAggregates();

            videoService.getVideos("kw", null, false);

            verify(videoRepository).searchPublicByKeyword("kw");
        }

        @Test
        void results_areSortedByIdDesc() {
            when(videoRepository.findAllPublic()).thenReturn(new ArrayList<>(Arrays.asList(
                    publicVideo(1L, 5L), publicVideo(3L, 5L), publicVideo(2L, 5L))));
            stubEmptyAggregates();

            List<VideoItem> result = videoService.getVideos(null, null, false);

            assertThat(result).extracting(VideoItem::getId).containsExactly(3L, 2L, 1L);
        }
    }

    @Nested
    class GetChannelProfile {

        @Test
        void missingUser_throwsNotFound() {
            when(userRepository.findById(5L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> videoService.getChannelProfile(5L, null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void loggedInSelf_marksIsMeTrue() {
            User u = new User();
            u.setId(5L); u.setNickname("nick"); u.setChannelName("Ch");
            when(userRepository.findById(5L)).thenReturn(Optional.of(u));
            when(subscriptionRepository.countByChannelOwnerId(5L)).thenReturn(10L);
            when(videoRepository.countPublicByOwnerId(5L)).thenReturn(3L);

            Map<String, Object> result = videoService.getChannelProfile(5L, 5L);

            assertThat(result.get("isMe")).isEqualTo(true);
            assertThat(result.get("subscribed")).isEqualTo(false);
            assertThat(result.get("subscriberCount")).isEqualTo(10L);
            assertThat(result.get("videoCount")).isEqualTo(3L);
        }

        @Test
        void othersChannel_reflectsSubscriptionState() {
            User u = new User();
            u.setId(5L); u.setNickname("nick");
            when(userRepository.findById(5L)).thenReturn(Optional.of(u));
            when(subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(99L, 5L)).thenReturn(true);

            Map<String, Object> result = videoService.getChannelProfile(5L, 99L);

            assertThat(result.get("isMe")).isEqualTo(false);
            assertThat(result.get("subscribed")).isEqualTo(true);
        }
    }

    @Nested
    class GetSubscriptionFeed {

        @Test
        void notLoggedIn_returnsEmpty() {
            Map<String, Object> result = videoService.getSubscriptionFeed(null, 0, 10);
            assertThat((List<?>) result.get("videos")).isEmpty();
            assertThat(result.get("hasMore")).isEqualTo(false);
            verifyNoInteractions(videoRepository);
        }

        @Test
        void noSubscriptions_returnsEmpty() {
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of());
            Map<String, Object> result = videoService.getSubscriptionFeed(1L, 0, 10);
            assertThat((List<?>) result.get("videos")).isEmpty();
            verify(videoRepository, never()).findByOwnerIdsPageable(any(), any());
        }

        @Test
        void withSubscriptions_returnsFeed() {
            Subscription s = new Subscription();
            s.setSubscriberId(1L); s.setChannelOwnerId(5L);
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of(s));
            Page<Video> page = new PageImpl<>(List.of(publicVideo(10L, 5L)), PageRequest.of(0, 10), 1);
            when(videoRepository.findByOwnerIdsPageable(eq(List.of(5L)), any(Pageable.class))).thenReturn(page);
            stubEmptyAggregates();
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(1L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(1L), anyList())).thenReturn(List.of());

            Map<String, Object> result = videoService.getSubscriptionFeed(1L, 0, 10);

            assertThat((List<?>) result.get("videos")).hasSize(1);
            assertThat(result.get("hasMore")).isEqualTo(false);
            assertThat(result.get("totalElements")).isEqualTo(1L);
        }
    }

    @Nested
    class GetFeed {

        @Test
        void ownerId_usesOwnerQuery() {
            when(videoRepository.findPublicByOwnerIdPageable(eq(5L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            videoService.getFeed(0, 10, null, null, 5L, null, null, null);
            verify(videoRepository).findPublicByOwnerIdPageable(eq(5L), any(Pageable.class));
        }

        @Test
        void popularWithKeyword_usesPopularKeywordQuery() {
            when(videoRepository.searchPublicByKeywordPageableOrderByViewCount(eq("kw"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            videoService.getFeed(0, 10, "kw", null, null, "popular", null, null);
            verify(videoRepository).searchPublicByKeywordPageableOrderByViewCount(eq("kw"), any(Pageable.class));
        }

        @Test
        void popularNoKeyword_usesPopularQuery() {
            when(videoRepository.findAllPublicPageableOrderByViewCount(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            videoService.getFeed(0, 10, null, null, null, null, "popular", null);
            verify(videoRepository).findAllPublicPageableOrderByViewCount(any(Pageable.class));
        }

        @Test
        void keywordAndCategory_usesCombinedQuery() {
            when(videoRepository.searchPublicByKeywordAndCategoryPageable(eq("kw"), eq("게임"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            videoService.getFeed(0, 10, "kw", "게임", null, null, null, null);
            verify(videoRepository).searchPublicByKeywordAndCategoryPageable(eq("kw"), eq("게임"), any(Pageable.class));
        }

        @Test
        void categoryOnly_usesCategoryQuery() {
            when(videoRepository.findAllPublicByCategoryPageable(eq("게임"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            videoService.getFeed(0, 10, null, "게임", null, null, null, null);
            verify(videoRepository).findAllPublicByCategoryPageable(eq("게임"), any(Pageable.class));
        }

        @Test
        void noFilters_usesDefaultQuery() {
            when(videoRepository.findAllPublicPageable(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            videoService.getFeed(0, 10, null, null, null, null, null, null);
            verify(videoRepository).findAllPublicPageable(any(Pageable.class));
        }
    }

    @Nested
    class PersonalizedFeed {

        private Video categorized(Long id, Long ownerId, String category) {
            Video v = publicVideo(id, ownerId);
            v.setCategory(category);
            return v;
        }

        private void stubToVideoItemsFor(Long userId) {
            when(videoLikeRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(commentRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(userId), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(userId), anyList())).thenReturn(List.of());
        }

        @SuppressWarnings("unchecked")
        private List<VideoItem> feedVideos(Map<String, Object> result) {
            return (List<VideoItem>) result.get("videos");
        }

        // 신호(좋아요·시청·구독)가 하나도 없으면 개인화하지 않고 기본 최신순으로 폴백한다.
        @Test
        void loggedInNoSignals_fallsBackToDefaultLatest() {
            when(videoLikeRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of());
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(1L)).thenReturn(List.of());
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of());
            when(videoRepository.findAllPublicPageable(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            videoService.getFeed(0, 10, null, null, null, null, null, 1L);

            verify(videoRepository).findAllPublicPageable(any(Pageable.class));
            verify(videoRepository, never()).findAllPublic();
        }

        // 구독 채널 영상은 상위로 부스트되고, 본인 업로드는 홈 추천에서 제외된다.
        @Test
        void subscribedChannel_isBoosted_andOwnUploadsExcluded() {
            Subscription sub = new Subscription();
            sub.setSubscriberId(1L); sub.setChannelOwnerId(5L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of());
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(1L)).thenReturn(List.of());
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of(sub));
            when(videoRepository.findAllById(anyList())).thenReturn(List.of());

            Video subscribed = publicVideo(10L, 5L);  // 구독 채널
            Video other      = publicVideo(20L, 7L);  // 비구독
            Video ownUpload  = publicVideo(30L, 1L);  // 본인 업로드 → 제외 대상
            when(videoRepository.findAllPublic())
                    .thenReturn(new ArrayList<>(Arrays.asList(other, subscribed, ownUpload)));
            stubToVideoItemsFor(1L);

            Map<String, Object> result = videoService.getFeed(0, 10, null, null, null, null, null, 1L);

            assertThat(feedVideos(result)).extracting(VideoItem::getId).containsExactly(10L, 20L);
            assertThat(result.get("totalElements")).isEqualTo(2L);
        }

        // 이미 본 영상은 크게 감점돼 안 본 영상보다 뒤로 밀린다(완전 제외는 아님).
        @Test
        void alreadyWatchedVideo_isDemoted() {
            VideoHistory h = new VideoHistory();
            h.setVideoId(20L); h.setUserId(1L); h.setWatchedAt(123L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of());
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(1L)).thenReturn(List.of(h));
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of());
            when(videoRepository.findAllById(anyList())).thenReturn(List.of());

            Video fresh   = publicVideo(10L, 5L);  // 안 본 영상
            Video watched = publicVideo(20L, 6L);  // 이미 본 영상
            when(videoRepository.findAllPublic())
                    .thenReturn(new ArrayList<>(Arrays.asList(watched, fresh)));
            stubToVideoItemsFor(1L);

            Map<String, Object> result = videoService.getFeed(0, 10, null, null, null, null, null, 1L);

            assertThat(feedVideos(result)).extracting(VideoItem::getId).containsExactly(10L, 20L);
        }

        // 좋아요한 영상의 카테고리와 같은 카테고리 영상이 선호도 점수로 상위에 온다.
        @Test
        void likedCategory_boostsSameCategoryVideos() {
            VideoLike like = new VideoLike(); like.setVideoId(100L); like.setUserId(1L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of(like));
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(1L)).thenReturn(List.of());
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of());

            Video likedVideo = categorized(100L, 9L, "게임");
            when(videoRepository.findAllById(anyList())).thenAnswer(inv -> {
                List<Long> ids = inv.getArgument(0);
                return ids.contains(100L) ? List.of(likedVideo) : List.of();
            });

            Video game = categorized(10L, 6L, "게임");
            Video cook = categorized(20L, 7L, "요리");
            when(videoRepository.findAllPublic())
                    .thenReturn(new ArrayList<>(Arrays.asList(cook, game)));
            stubToVideoItemsFor(1L);

            Map<String, Object> result = videoService.getFeed(0, 10, null, null, null, null, null, 1L);

            assertThat(feedVideos(result)).extracting(VideoItem::getId).containsExactly(10L, 20L);
        }

        // 개인화 랭킹도 페이지네이션 슬라이스와 hasMore를 올바르게 계산한다.
        @Test
        void paginatesRankedCandidates() {
            Subscription sub = new Subscription();
            sub.setSubscriberId(1L); sub.setChannelOwnerId(5L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of());
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(1L)).thenReturn(List.of());
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of(sub));
            when(videoRepository.findAllById(anyList())).thenReturn(List.of());

            List<Video> all = new ArrayList<>();
            for (long i = 1; i <= 5; i++) all.add(publicVideo(i, 6L));
            when(videoRepository.findAllPublic()).thenReturn(all);
            stubToVideoItemsFor(1L);

            Map<String, Object> page0 = videoService.getFeed(0, 2, null, null, null, null, null, 1L);
            assertThat(feedVideos(page0)).hasSize(2);
            assertThat(page0.get("hasMore")).isEqualTo(true);
            assertThat(page0.get("totalElements")).isEqualTo(5L);

            Map<String, Object> lastPage = videoService.getFeed(2, 2, null, null, null, null, null, 1L);
            assertThat(feedVideos(lastPage)).hasSize(1);
            assertThat(lastPage.get("hasMore")).isEqualTo(false);
        }

        // 무한 스크롤(page>0)은 page 0에서 만든 랭킹 스냅샷을 재사용해 재계산하지 않는다
        @Test
        void paginationReusesRankingSnapshot() {
            Subscription sub = new Subscription();
            sub.setSubscriberId(1L); sub.setChannelOwnerId(5L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of());
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(1L)).thenReturn(List.of());
            when(subscriptionRepository.findBySubscriberId(1L)).thenReturn(List.of(sub));
            when(videoRepository.findAllById(anyList())).thenReturn(List.of());
            List<Video> all = new ArrayList<>();
            for (long i = 1; i <= 4; i++) all.add(publicVideo(i, 6L));
            when(videoRepository.findAllPublic()).thenReturn(all);
            stubToVideoItemsFor(1L);

            videoService.getFeed(0, 2, null, null, null, null, null, 1L); // 랭킹 계산 + 스냅샷 저장
            videoService.getFeed(1, 2, null, null, null, null, null, 1L); // 스냅샷 재사용(재계산 X)

            verify(videoRepository, times(1)).findAllPublic();
            verify(subscriptionRepository, times(1)).findBySubscriberId(1L);
        }
    }

    @Nested
    class GetVideoById {

        @Test
        void missing_throwsNotFound() {
            when(videoRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> videoService.getVideoById(1L, null, false))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void privateVideo_nonOwnerNonAdmin_throwsForbidden() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(privateVideo(1L, 5L)));
            assertThatThrownBy(() -> videoService.getVideoById(1L, 99L, false))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("비공개");
        }

        @Test
        void privateVideo_owner_isAllowed() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(privateVideo(1L, 5L)));
            VideoItem item = videoService.getVideoById(1L, 5L, false);
            assertThat(item.getId()).isEqualTo(1L);
        }

        @Test
        void privateVideo_admin_isAllowed() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(privateVideo(1L, 5L)));
            VideoItem item = videoService.getVideoById(1L, 99L, true);
            assertThat(item.getId()).isEqualTo(1L);
        }

        @Test
        void publicVideo_populatesCountsAndFlags() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(publicVideo(1L, 5L)));
            when(videoLikeRepository.countByVideoId(1L)).thenReturn(4L);
            when(commentRepository.countByVideoIdAndParentIdIsNull(1L)).thenReturn(2L);
            when(videoLikeRepository.existsByVideoIdAndUserId(1L, 99L)).thenReturn(true);
            when(videoSaveRepository.existsByVideoIdAndUserId(1L, 99L)).thenReturn(false);
            when(videoDislikeRepository.countByVideoId(1L)).thenReturn(1L);
            when(videoDislikeRepository.existsByVideoIdAndUserId(1L, 99L)).thenReturn(true);

            VideoItem item = videoService.getVideoById(1L, 99L, false);

            assertThat(item.getLikeCount()).isEqualTo(4L);
            assertThat(item.getCommentCount()).isEqualTo(2L);
            assertThat(item.isLikedByMe()).isTrue();
            assertThat(item.isSavedByMe()).isFalse();
            assertThat(item.getDislikeCount()).isEqualTo(1L);
            assertThat(item.isDislikedByMe()).isTrue();
        }
    }

    @Nested
    class Studio {

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> videoService.getStudioVideos(null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void returnsOwnerVideos() {
            when(videoRepository.findByOwnerIdOrderByIdDesc(5L))
                    .thenReturn(List.of(publicVideo(1L, 5L), privateVideo(2L, 5L)));
            stubEmptyAggregates();
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(5L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(5L), anyList())).thenReturn(List.of());

            List<VideoItem> result = videoService.getStudioVideos(5L);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class UpdateVideo {

        private VideoUpdateRequest fullRequest() {
            VideoUpdateRequest r = new VideoUpdateRequest();
            r.setTitle("t"); r.setDescription("d"); r.setChannel("c");
            r.setDuration("1:23"); r.setThumbnail("th"); r.setEmbedUrl("");
            r.setAvatar(""); r.setCategory(""); r.setVisibility("");
            return r;
        }

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> videoService.updateVideo(1L, null, fullRequest()))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void missing_throwsNotFound() {
            when(videoRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> videoService.updateVideo(1L, 5L, fullRequest()))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void notOwner_throwsForbidden() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(publicVideo(1L, 5L)));
            assertThatThrownBy(() -> videoService.updateVideo(1L, 999L, fullRequest()))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void blankTitle_throwsBadRequest() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(publicVideo(1L, 5L)));
            VideoUpdateRequest r = fullRequest();
            r.setTitle("  ");
            assertThatThrownBy(() -> videoService.updateVideo(1L, 5L, r))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("제목");
        }

        @Test
        void valid_defaultsMissingCategoryAndVisibility() {
            Video existing = publicVideo(1L, 5L);
            when(videoRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(videoRepository.save(existing)).thenReturn(existing);

            VideoUpdateRequest r = fullRequest();
            r.setCategory(null); r.setVisibility(null);
            videoService.updateVideo(1L, 5L, r);

            assertThat(existing.getCategory()).isEqualTo("기타");
            assertThat(existing.getVisibility()).isEqualTo("공개");
        }

        @Test
        void valid_trimsFields() {
            Video existing = publicVideo(1L, 5L);
            when(videoRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(videoRepository.save(existing)).thenReturn(existing);

            VideoUpdateRequest r = fullRequest();
            r.setTitle("  hello  ");
            r.setDescription("  desc  ");
            videoService.updateVideo(1L, 5L, r);

            assertThat(existing.getTitle()).isEqualTo("hello");
            assertThat(existing.getDescription()).isEqualTo("desc");
        }
    }

    @Nested
    class DeleteVideo {

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> videoService.deleteVideo(1L, null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void notOwner_throwsForbidden() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(publicVideo(1L, 5L)));
            assertThatThrownBy(() -> videoService.deleteVideo(1L, 999L))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void owner_deletesVideo() {
            Video v = publicVideo(1L, 5L);
            when(videoRepository.findById(1L)).thenReturn(Optional.of(v));

            videoService.deleteVideo(1L, 5L);

            verify(videoRepository).delete(v);
        }
    }

    @Nested
    class LikedAndSaved {

        @Test
        void notLoggedIn_returnsEmpty() {
            assertThat(videoService.getMyLikedVideos(null, false)).isEmpty();
            assertThat(videoService.getMySavedVideos(null, false)).isEmpty();
        }

        @Test
        void noLikes_returnsEmpty() {
            when(videoLikeRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of());
            assertThat(videoService.getMyLikedVideos(1L, false)).isEmpty();
        }

        @Test
        void privateOthersVideo_isFilteredOutForNonAdmin() {
            VideoLike l1 = new VideoLike(); l1.setVideoId(1L); l1.setUserId(99L);
            VideoLike l2 = new VideoLike(); l2.setVideoId(2L); l2.setUserId(99L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(99L)).thenReturn(List.of(l1, l2));
            when(videoRepository.findAllById(List.of(1L, 2L)))
                    .thenReturn(List.of(publicVideo(1L, 5L), privateVideo(2L, 5L)));
            stubEmptyAggregates();
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(99L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(99L), anyList())).thenReturn(List.of());

            List<VideoItem> result = videoService.getMyLikedVideos(99L, false);

            assertThat(result).extracting(VideoItem::getId).containsExactly(1L);
        }

        @Test
        void privateOwnVideo_isKeptForNonAdmin() {
            VideoLike l1 = new VideoLike(); l1.setVideoId(2L); l1.setUserId(5L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(5L)).thenReturn(List.of(l1));
            when(videoRepository.findAllById(List.of(2L)))
                    .thenReturn(List.of(privateVideo(2L, 5L)));
            stubEmptyAggregates();
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(5L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(5L), anyList())).thenReturn(List.of());

            List<VideoItem> result = videoService.getMyLikedVideos(5L, false);

            assertThat(result).hasSize(1);
        }

        @Test
        void adminSeesPrivateVideosOfOthers() {
            VideoSave s1 = new VideoSave(); s1.setVideoId(2L); s1.setUserId(99L);
            when(videoSaveRepository.findByUserIdOrderByIdDesc(99L)).thenReturn(List.of(s1));
            when(videoRepository.findAllById(List.of(2L)))
                    .thenReturn(List.of(privateVideo(2L, 5L)));
            stubEmptyAggregates();
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(99L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(99L), anyList())).thenReturn(List.of());

            List<VideoItem> result = videoService.getMySavedVideos(99L, true);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class ToVideoItems {

        @Test
        void emptyList_returnsEmpty() {
            List<VideoItem> result = videoService.toVideoItems(List.of(), 1L);
            assertThat(result).isEmpty();
            verifyNoInteractions(videoLikeRepository, videoSaveRepository, commentRepository);
        }

        @Test
        void aggregatesCountsAndFlags() {
            List<Video> videos = List.of(publicVideo(10L, 5L), publicVideo(20L, 6L));
            when(videoLikeRepository.countByVideoIdIn(List.of(10L, 20L)))
                    .thenReturn(List.<Object[]>of(new Object[]{10L, 7L}));
            when(commentRepository.countByVideoIdIn(List.of(10L, 20L)))
                    .thenReturn(List.<Object[]>of(new Object[]{20L, 3L}));
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(1L), eq(List.of(10L, 20L))))
                    .thenReturn(List.of(10L));
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(1L), eq(List.of(10L, 20L))))
                    .thenReturn(List.of(20L));

            List<VideoItem> result = videoService.toVideoItems(videos, 1L);

            VideoItem v10 = result.stream().filter(v -> v.getId() == 10L).findFirst().orElseThrow();
            VideoItem v20 = result.stream().filter(v -> v.getId() == 20L).findFirst().orElseThrow();
            assertThat(v10.getLikeCount()).isEqualTo(7L);
            assertThat(v10.isLikedByMe()).isTrue();
            assertThat(v10.isSavedByMe()).isFalse();
            assertThat(v20.getCommentCount()).isEqualTo(3L);
            assertThat(v20.isSavedByMe()).isTrue();
        }

        @Test
        void anonymousUser_hasNoLikedOrSavedFlags() {
            when(videoLikeRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(commentRepository.countByVideoIdIn(anyList())).thenReturn(List.of());

            List<VideoItem> result = videoService.toVideoItems(List.of(publicVideo(10L, 5L)), null);

            assertThat(result.get(0).isLikedByMe()).isFalse();
            assertThat(result.get(0).isSavedByMe()).isFalse();
            verify(videoLikeRepository, never()).findLikedVideoIdsByUserId(anyLong(), anyList());
            verify(videoSaveRepository, never()).findSavedVideoIdsByUserId(anyLong(), anyList());
        }
    }

    @Nested
    class GetRelatedVideos {

        private Video video(Long id, Long ownerId, String category, String channel) {
            Video v = publicVideo(id, ownerId);
            v.setCategory(category);
            v.setChannel(channel);
            return v;
        }

        private void stubCountsEmpty() {
            when(videoLikeRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
            when(commentRepository.countByVideoIdIn(anyList())).thenReturn(List.of());
        }

        @Test
        void notFound_throws404() {
            when(videoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> videoService.getRelatedVideos(99L, null, 12))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("404");
        }

        @Test
        void excludesBaseVideo_andRanksSameCategoryFirst() {
            Video base = video(1L, 5L, "게임", "chA");
            Video sameCategory = video(2L, 6L, "게임", "chB");
            Video other = video(3L, 7L, "요리", "chC");
            when(videoRepository.findById(1L)).thenReturn(Optional.of(base));
            when(videoRepository.findRelatedByCategory(eq("게임"), eq(1L), any()))
                    .thenReturn(new ArrayList<>(List.of(sameCategory)));
            when(videoRepository.findPopularPublicExcluding(eq(1L), any()))
                    .thenReturn(new ArrayList<>(Arrays.asList(sameCategory, other)));
            stubCountsEmpty();

            Map<String, Object> result = videoService.getRelatedVideos(1L, null, 12);

            @SuppressWarnings("unchecked")
            List<VideoItem> recommended = (List<VideoItem>) result.get("recommended");
            assertThat(recommended).extracting(VideoItem::getId).doesNotContain(1L);
            // 카테고리 일치 +50점은 노이즈(최대 3점)보다 커서 항상 먼저 온다
            assertThat(recommended.get(0).getId()).isEqualTo(2L);
        }

        @Test
        void channelList_containsOnlySameOwnerVideos() {
            Video base = video(1L, 5L, "게임", "chA");
            Video sameOwner = video(2L, 5L, "요리", "chA");
            Video otherOwner = video(3L, 7L, "게임", "chC");
            when(videoRepository.findById(1L)).thenReturn(Optional.of(base));
            when(videoRepository.findRelatedByOwner(eq(5L), eq(1L), any()))
                    .thenReturn(new ArrayList<>(List.of(sameOwner)));
            when(videoRepository.findRelatedByCategory(eq("게임"), eq(1L), any()))
                    .thenReturn(new ArrayList<>(List.of(otherOwner)));
            when(videoRepository.findPopularPublicExcluding(eq(1L), any()))
                    .thenReturn(new ArrayList<>(Arrays.asList(sameOwner, otherOwner)));
            stubCountsEmpty();

            Map<String, Object> result = videoService.getRelatedVideos(1L, null, 12);

            @SuppressWarnings("unchecked")
            List<VideoItem> channel = (List<VideoItem>) result.get("channel");
            assertThat(channel).extracting(VideoItem::getId).containsExactly(2L);
        }

        @Test
        void respectsLimit() {
            Video base = video(1L, 5L, "게임", "chA");
            List<Video> categoryMatches = new ArrayList<>();
            for (long i = 2; i <= 20; i++) {
                categoryMatches.add(video(i, 6L, "게임", "chB"));
            }
            when(videoRepository.findById(1L)).thenReturn(Optional.of(base));
            when(videoRepository.findRelatedByCategory(eq("게임"), eq(1L), any()))
                    .thenReturn(new ArrayList<>(categoryMatches));
            stubCountsEmpty();

            Map<String, Object> result = videoService.getRelatedVideos(1L, null, 5);

            @SuppressWarnings("unchecked")
            List<VideoItem> recommended = (List<VideoItem>) result.get("recommended");
            assertThat(recommended).hasSize(5);
        }

        // 로그인 유저의 구독 채널 관련영상은 개인화 보너스로 같은 카테고리 내에서 상위로 온다
        @Test
        void loggedInUser_subscribedChannelRelatedVideo_isBoosted() {
            Video base = video(1L, 5L, "게임", "chA");
            Video plain = video(2L, 6L, "게임", "chB");       // 같은 카테고리
            Video subscribed = video(3L, 9L, "게임", "chC");  // 같은 카테고리 + 구독 채널
            when(videoRepository.findById(1L)).thenReturn(Optional.of(base));
            when(videoRepository.findRelatedByCategory(eq("게임"), eq(1L), any()))
                    .thenReturn(new ArrayList<>(Arrays.asList(plain, subscribed)));
            when(videoRepository.findPopularPublicExcluding(eq(1L), any()))
                    .thenReturn(new ArrayList<>(Arrays.asList(plain, subscribed)));
            stubCountsEmpty();

            Subscription sub = new Subscription();
            sub.setSubscriberId(99L); sub.setChannelOwnerId(9L);
            when(videoLikeRepository.findByUserIdOrderByIdDesc(99L)).thenReturn(List.of());
            when(videoHistoryRepository.findByUserIdOrderByWatchedAtDesc(99L)).thenReturn(List.of());
            when(subscriptionRepository.findBySubscriberId(99L)).thenReturn(List.of(sub));
            when(videoLikeRepository.findLikedVideoIdsByUserId(eq(99L), anyList())).thenReturn(List.of());
            when(videoSaveRepository.findSavedVideoIdsByUserId(eq(99L), anyList())).thenReturn(List.of());

            Map<String, Object> result = videoService.getRelatedVideos(1L, 99L, 12);

            @SuppressWarnings("unchecked")
            List<VideoItem> recommended = (List<VideoItem>) result.get("recommended");
            assertThat(recommended.get(0).getId()).isEqualTo(3L);
        }
    }

    // 개인화 피드 스냅샷 저장소: TTL 만료 정리 + 크기 상한으로 무한 증식을 막는지 검증(시간은 인자로 주입).
    @Nested
    class FeedSnapshotStoreCache {

        private VideoService.FeedSnapshot snap(long createdAtMs) {
            return new VideoService.FeedSnapshot(List.of(), createdAtMs);
        }

        // TTL 이내면 신선한 스냅샷을 반환하고, 지나면 null + 만료 엔트리를 제거한다.
        @Test
        void getFresh_returnsWithinTtl_removesAfterExpiry() {
            VideoService.FeedSnapshotStore store = new VideoService.FeedSnapshotStore(1000, 10);
            store.put(1L, snap(0), 0);

            assertThat(store.getFresh(1L, 500)).isNotNull();   // 0.5s < 1s TTL → 신선
            assertThat(store.getFresh(1L, 1500)).isNull();     // 1.5s >= 1s TTL → 만료
            assertThat(store.size()).isZero();                 // 만료 엔트리 제거됨
        }

        // 상한을 넘으면 가장 오래된 엔트리가 축출되고 크기가 상한을 넘지 않는다.
        @Test
        void put_evictsOldest_whenOverCapacity() {
            VideoService.FeedSnapshotStore store = new VideoService.FeedSnapshotStore(100_000, 3);
            store.put(1L, snap(10), 10);
            store.put(2L, snap(20), 20);
            store.put(3L, snap(30), 30);
            store.put(4L, snap(40), 40); // 상한(3) 초과 → 가장 오래된 user1 축출

            assertThat(store.size()).isEqualTo(3);
            assertThat(store.getFresh(1L, 50)).isNull();       // user1 축출됨
            assertThat(store.getFresh(4L, 50)).isNotNull();    // 최신은 유지
        }

        // 같은 유저를 다시 저장하는 것은 크기를 늘리지 않는다(축출 트리거 아님).
        @Test
        void put_sameUser_doesNotGrow() {
            VideoService.FeedSnapshotStore store = new VideoService.FeedSnapshotStore(100_000, 2);
            store.put(1L, snap(10), 10);
            store.put(1L, snap(20), 20);

            assertThat(store.size()).isEqualTo(1);
        }

        // TTL이 지난 뒤 새로 저장하면, 저장 시점 스윕으로 만료된 다른 유저 엔트리들이 정리된다.
        @Test
        void put_sweepsExpiredEntries() {
            VideoService.FeedSnapshotStore store = new VideoService.FeedSnapshotStore(1000, 100);
            store.put(1L, snap(0), 0);
            store.put(2L, snap(0), 0);

            store.put(3L, snap(2000), 2000); // 2s 후: 스윕으로 만료된 user1,2 제거 + user3 저장

            assertThat(store.size()).isEqualTo(1);
            assertThat(store.getFresh(3L, 2000)).isNotNull();
        }
    }

    // 공개 영상 후보 공유 캐시: TTL 동안 loader 1회만 호출·공유, 만료·invalidate 시 재로드, 반환은 불변.
    @Nested
    class PublicVideoCandidateCache {

        private List<Video> twoVideos() {
            return List.of(publicVideo(1L, 5L), publicVideo(2L, 6L));
        }

        // TTL 이내에는 loader를 다시 호출하지 않고 같은 공유 인스턴스를 반환한다.
        @Test
        void get_withinTtl_loadsOnceAndShares() {
            VideoService.PublicVideoCache cache = new VideoService.PublicVideoCache(1000);
            AtomicInteger calls = new AtomicInteger();

            List<Video> a = cache.get(0, () -> { calls.incrementAndGet(); return twoVideos(); });
            List<Video> b = cache.get(500, () -> { calls.incrementAndGet(); return twoVideos(); });

            assertThat(calls.get()).isEqualTo(1);   // 두 번째는 캐시 재사용
            assertThat(b).isSameAs(a);              // 같은 공유 인스턴스
            assertThat(a).hasSize(2);
        }

        // TTL이 지나면 loader를 다시 호출해 갱신한다.
        @Test
        void get_afterTtl_reloads() {
            VideoService.PublicVideoCache cache = new VideoService.PublicVideoCache(1000);
            AtomicInteger calls = new AtomicInteger();

            cache.get(0, () -> { calls.incrementAndGet(); return twoVideos(); });
            cache.get(1500, () -> { calls.incrementAndGet(); return twoVideos(); }); // 1.5s >= 1s TTL

            assertThat(calls.get()).isEqualTo(2);
        }

        // invalidate() 후에는 TTL 이내라도 다시 로드한다.
        @Test
        void invalidate_forcesReload() {
            VideoService.PublicVideoCache cache = new VideoService.PublicVideoCache(1000);
            AtomicInteger calls = new AtomicInteger();

            cache.get(0, () -> { calls.incrementAndGet(); return twoVideos(); });
            cache.invalidate();
            cache.get(100, () -> { calls.incrementAndGet(); return twoVideos(); }); // TTL 이내지만 무효화됨

            assertThat(calls.get()).isEqualTo(2);
        }

        // 반환 리스트는 불변이라 호출부가 변경할 수 없다(공유 캐시 오염 방지).
        @Test
        void returnedList_isImmutable() {
            VideoService.PublicVideoCache cache = new VideoService.PublicVideoCache(1000);
            List<Video> list = cache.get(0, this::twoVideos);

            assertThatThrownBy(() -> list.add(publicVideo(9L, 9L)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
