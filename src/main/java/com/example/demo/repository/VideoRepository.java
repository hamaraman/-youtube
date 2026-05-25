package com.example.demo.repository;

import com.example.demo.entity.Video;
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

    List<Video> findByOwnerIdIsNull();

    List<Video> findByChannel(String channel);

    Optional<Video> findByTitle(String title);

    List<Video> findByTitleContaining(String title);

    Optional<Video> findByVideoUrl(String videoUrl);
}
