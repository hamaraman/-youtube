package com.example.demo.repository;

import com.example.demo.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @Query("SELECT v FROM Video v WHERE v.visibility IS NULL OR v.visibility != '비공개'")
    List<Video> findAllPublic();

    @Query("SELECT v FROM Video v WHERE (v.visibility IS NULL OR v.visibility != '비공개') OR v.ownerId = :ownerId")
    List<Video> findPublicOrOwnedBy(@Param("ownerId") Long ownerId);

    @Query("SELECT v FROM Video v WHERE " +
           "(LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.channel) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (v.visibility IS NULL OR v.visibility != '비공개')")
    List<Video> searchPublicByKeyword(@Param("keyword") String keyword);

    @Query("SELECT v FROM Video v WHERE " +
           "(LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.channel) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND ((v.visibility IS NULL OR v.visibility != '비공개') OR v.ownerId = :ownerId)")
    List<Video> searchPublicOrOwnedByKeyword(@Param("keyword") String keyword, @Param("ownerId") Long ownerId);

    @Query("SELECT v FROM Video v WHERE " +
           "LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.channel) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Video> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT v FROM Video v WHERE v.visibility IS NULL OR v.visibility != '비공개' ORDER BY v.id DESC")
    Page<Video> findAllPublicPageable(Pageable pageable);

    @Query("SELECT v FROM Video v WHERE v.visibility IS NULL OR v.visibility != '비공개' ORDER BY v.viewCount DESC, v.id DESC")
    Page<Video> findAllPublicPageableOrderByViewCount(Pageable pageable);

    @Query("SELECT v FROM Video v WHERE (v.visibility IS NULL OR v.visibility != '비공개') AND v.category = :category ORDER BY v.id DESC")
    Page<Video> findAllPublicByCategoryPageable(@Param("category") String category, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE " +
           "(LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.channel) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (v.visibility IS NULL OR v.visibility != '비공개') ORDER BY v.id DESC")
    Page<Video> searchPublicByKeywordPageable(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE " +
           "(LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.channel) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (v.visibility IS NULL OR v.visibility != '비공개') ORDER BY v.viewCount DESC, v.id DESC")
    Page<Video> searchPublicByKeywordPageableOrderByViewCount(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE " +
           "(LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.channel) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (v.visibility IS NULL OR v.visibility != '비공개') AND v.category = :category ORDER BY v.id DESC")
    Page<Video> searchPublicByKeywordAndCategoryPageable(@Param("keyword") String keyword, @Param("category") String category, Pageable pageable);

    @Query("SELECT DISTINCT v.category FROM Video v WHERE v.category IS NOT NULL AND v.category <> '' AND (v.visibility IS NULL OR v.visibility != '비공개') ORDER BY v.category ASC")
    List<String> findAllPublicCategories();

    // 시청 페이지 추천 후보 1차 필터 (전체 공개 영상 덤프 대신 SQL에서 좁힌다)
    @Query("SELECT v FROM Video v WHERE (v.visibility IS NULL OR v.visibility != '비공개') AND v.id <> :excludeId AND v.category = :category ORDER BY v.viewCount DESC, v.id DESC")
    List<Video> findRelatedByCategory(@Param("category") String category, @Param("excludeId") Long excludeId, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE (v.visibility IS NULL OR v.visibility != '비공개') AND v.id <> :excludeId AND v.ownerId = :ownerId ORDER BY v.id DESC")
    List<Video> findRelatedByOwner(@Param("ownerId") Long ownerId, @Param("excludeId") Long excludeId, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE (v.visibility IS NULL OR v.visibility != '비공개') AND v.id <> :excludeId ORDER BY v.viewCount DESC, v.id DESC")
    List<Video> findPopularPublicExcluding(@Param("excludeId") Long excludeId, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE v.ownerId IN :ownerIds AND (v.visibility IS NULL OR v.visibility != '비공개') ORDER BY v.id DESC")
    Page<Video> findByOwnerIdsPageable(@Param("ownerIds") java.util.List<Long> ownerIds, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE v.ownerId = :ownerId AND (v.visibility IS NULL OR v.visibility != '비공개') ORDER BY v.id DESC")
    Page<Video> findPublicByOwnerIdPageable(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT COUNT(v) FROM Video v WHERE v.ownerId = :ownerId AND (v.visibility IS NULL OR v.visibility != '비공개')")
    long countPublicByOwnerId(@Param("ownerId") Long ownerId);

    List<Video> findByOwnerIdOrderByIdDesc(Long ownerId);

    List<Video> findByOwnerIdIsNull();

    List<Video> findByChannel(String channel);

    Optional<Video> findByTitle(String title);

    List<Video> findByTitleContaining(String title);

    Optional<Video> findByVideoUrl(String videoUrl);
}
