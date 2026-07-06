package com.example.demo.repository;

import com.example.demo.entity.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class VideoRepositoryTest {

    @Autowired private VideoRepository videoRepository;

    private Video ownerA_publicGame;
    private Video ownerA_privateGame;
    private Video ownerB_publicMusic;
    private Video ownerB_publicGameHighViews;

    @BeforeEach
    void setUp() {
        ownerA_publicGame = save(1L, "Zelda 공략", "GameChannel", "게임", "공개", 100);
        ownerA_privateGame = save(1L, "숨겨진 영상", "GameChannel", "게임", "비공개", 50);
        ownerB_publicMusic = save(2L, "JPop cover", "MusicHub", "음악", "공개", 300);
        ownerB_publicGameHighViews = save(2L, "Elden Ring 리뷰", "MusicHub", "게임", "공개", 999);
    }

    private Video save(Long ownerId, String title, String channel, String category, String visibility, long views) {
        Video v = new Video();
        v.setOwnerId(ownerId);
        v.setTitle(title);
        v.setChannel(channel);
        v.setCategory(category);
        v.setVisibility(visibility);
        v.setDuration("1:00");
        v.setThumbnail("thumb.png");
        v.setViewCount(views);
        return videoRepository.save(v);
    }

    @Test
    void findAllPublic_excludesPrivate() {
        List<Video> videos = videoRepository.findAllPublic();
        assertThat(videos).extracting(Video::getTitle)
                .doesNotContain("숨겨진 영상")
                .contains("Zelda 공략", "JPop cover", "Elden Ring 리뷰");
    }

    @Test
    void findPublicOrOwnedBy_includesPrivateWhenOwner() {
        List<Video> asOwnerA = videoRepository.findPublicOrOwnedBy(1L);
        assertThat(asOwnerA).extracting(Video::getTitle)
                .contains("숨겨진 영상", "Zelda 공략", "JPop cover", "Elden Ring 리뷰");

        List<Video> asOtherUser = videoRepository.findPublicOrOwnedBy(999L);
        assertThat(asOtherUser).extracting(Video::getTitle)
                .doesNotContain("숨겨진 영상");
    }

    @Test
    void searchPublicByKeyword_matchesTitleOrChannel_caseInsensitive() {
        List<Video> byTitle = videoRepository.searchPublicByKeyword("elden");
        assertThat(byTitle).extracting(Video::getTitle).containsExactly("Elden Ring 리뷰");

        List<Video> byChannel = videoRepository.searchPublicByKeyword("gamechannel");
        assertThat(byChannel).extracting(Video::getTitle).containsExactly("Zelda 공략");
    }

    @Test
    void searchPublicByKeyword_excludesPrivate() {
        List<Video> results = videoRepository.searchPublicByKeyword("숨겨진");
        assertThat(results).isEmpty();
    }

    @Test
    void findAllPublicPageable_orderedByIdDesc() {
        Page<Video> page = videoRepository.findAllPublicPageable(PageRequest.of(0, 10));
        assertThat(page.getContent())
                .isSortedAccordingTo((a, b) -> Long.compare(b.getId(), a.getId()));
    }

    @Test
    void findAllPublicPageableOrderByViewCount_ordersByViewsThenId() {
        Page<Video> page = videoRepository.findAllPublicPageableOrderByViewCount(PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Video::getTitle)
                .containsExactly("Elden Ring 리뷰", "JPop cover", "Zelda 공략");
    }

    @Test
    void findAllPublicByCategoryPageable_filtersByCategory() {
        Page<Video> games = videoRepository.findAllPublicByCategoryPageable("게임", PageRequest.of(0, 10));
        assertThat(games).extracting(Video::getTitle)
                .containsExactlyInAnyOrder("Zelda 공략", "Elden Ring 리뷰");
    }

    @Test
    void searchPublicByKeywordAndCategoryPageable_combinesBoth() {
        Page<Video> results = videoRepository.searchPublicByKeywordAndCategoryPageable(
                "리뷰", "게임", PageRequest.of(0, 10));
        assertThat(results).extracting(Video::getTitle).containsExactly("Elden Ring 리뷰");
    }

    @Test
    void findAllPublicCategories_returnsDistinctSorted() {
        List<String> categories = videoRepository.findAllPublicCategories();
        assertThat(categories).containsExactly("게임", "음악");
    }

    @Test
    void findByOwnerIdsPageable_returnsOnlyGivenOwners_publicOnly() {
        Page<Video> videos = videoRepository.findByOwnerIdsPageable(
                List.of(1L), PageRequest.of(0, 10));
        assertThat(videos).extracting(Video::getTitle).containsExactly("Zelda 공략");
    }

    @Test
    void findPublicByOwnerIdPageable_excludesPrivate() {
        Page<Video> ownerA = videoRepository.findPublicByOwnerIdPageable(1L, PageRequest.of(0, 10));
        assertThat(ownerA).extracting(Video::getTitle).containsExactly("Zelda 공략");
    }

    @Test
    void countPublicByOwnerId_countsOnlyPublic() {
        assertThat(videoRepository.countPublicByOwnerId(1L)).isEqualTo(1L);
        assertThat(videoRepository.countPublicByOwnerId(2L)).isEqualTo(2L);
    }

    @Test
    void findByOwnerIdOrderByIdDesc_includesPrivate() {
        List<Video> videos = videoRepository.findByOwnerIdOrderByIdDesc(1L);
        assertThat(videos).extracting(Video::getTitle)
                .containsExactly("숨겨진 영상", "Zelda 공략");
    }

    @Test
    void findRelatedByCategory_excludesBaseAndPrivate_ordersByViews() {
        List<Video> results = videoRepository.findRelatedByCategory(
                "게임", ownerA_publicGame.getId(), PageRequest.of(0, 10));
        // 같은 카테고리 공개 영상 중 base(Zelda)와 비공개(숨겨진 영상)는 빠진다
        assertThat(results).extracting(Video::getTitle).containsExactly("Elden Ring 리뷰");
    }

    @Test
    void findRelatedByOwner_returnsOwnersPublic_excludingBaseAndPrivate() {
        List<Video> forOwnerB = videoRepository.findRelatedByOwner(
                2L, ownerB_publicMusic.getId(), PageRequest.of(0, 10));
        assertThat(forOwnerB).extracting(Video::getTitle).containsExactly("Elden Ring 리뷰");

        // 소유자 A는 base(Zelda)를 빼면 비공개 영상만 남으므로 결과 없음
        List<Video> forOwnerA = videoRepository.findRelatedByOwner(
                1L, ownerA_publicGame.getId(), PageRequest.of(0, 10));
        assertThat(forOwnerA).isEmpty();
    }

    @Test
    void findPopularPublicExcluding_ordersByViews_excludesBaseAndPrivate() {
        List<Video> results = videoRepository.findPopularPublicExcluding(
                ownerB_publicGameHighViews.getId(), PageRequest.of(0, 10));
        // 조회수 순: JPop cover(300) → Zelda(100), base(Elden 999)와 비공개는 제외
        assertThat(results).extracting(Video::getTitle).containsExactly("JPop cover", "Zelda 공략");
    }
}
