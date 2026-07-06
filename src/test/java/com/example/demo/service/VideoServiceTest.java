package com.example.demo.service;

import com.example.demo.controller.VideoController.VideoItem;
import com.example.demo.controller.VideoController.VideoUpdateRequest;
import com.example.demo.entity.Subscription;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
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
    }
}
