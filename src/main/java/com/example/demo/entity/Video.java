package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String channel;

    private String avatar;

    private String category;

    @Column(nullable = false)
    private String duration;

    private String visibility;

    private String embedUrl;

    @Column(nullable = false)
    private String thumbnail;

    private String videoUrl;

    private String videoUrl1080;

    private String videoUrl720;

    private String videoUrl480;

    private String videoUrl360;

    private String dateText;

    @Column(name = "view_count")
    private long viewCount = 0;

    public Video() {
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getChannel() {
        return channel;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getCategory() {
        return category;
    }

    public String getDuration() {
        return duration;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getEmbedUrl() {
        return embedUrl;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getVideoUrl1080() {
        return videoUrl1080;
    }

    public String getVideoUrl720() {
        return videoUrl720;
    }

    public String getVideoUrl480() {
        return videoUrl480;
    }

    public String getVideoUrl360() {
        return videoUrl360;
    }

    public String getDateText() {
        return dateText;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public void setEmbedUrl(String embedUrl) {
        this.embedUrl = embedUrl;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public void setVideoUrl1080(String videoUrl1080) {
        this.videoUrl1080 = videoUrl1080;
    }

    public void setVideoUrl720(String videoUrl720) {
        this.videoUrl720 = videoUrl720;
    }

    public void setVideoUrl480(String videoUrl480) {
        this.videoUrl480 = videoUrl480;
    }

    public void setVideoUrl360(String videoUrl360) {
        this.videoUrl360 = videoUrl360;
    }

    public void setDateText(String dateText) {
        this.dateText = dateText;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }
}