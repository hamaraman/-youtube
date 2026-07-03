package com.example.demo.repository;

import com.example.demo.entity.Playlist;
import com.example.demo.entity.PlaylistVideo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class PlaylistRepositoryTest {

    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private PlaylistVideoRepository playlistVideoRepository;

    private Long user1PlaylistOldId;
    private Long user1PlaylistNewId;
    private Long user2PlaylistId;

    @BeforeEach
    void setUp() throws InterruptedException {
        user1PlaylistOldId = createPlaylist(1L, "old").getId();
        Thread.sleep(5); // ensure different createdAt
        user1PlaylistNewId = createPlaylist(1L, "new").getId();
        user2PlaylistId = createPlaylist(2L, "other").getId();

        addVideoToPlaylist(user1PlaylistNewId, 100L);
        addVideoToPlaylist(user1PlaylistNewId, 200L);
        addVideoToPlaylist(user1PlaylistOldId, 300L);
    }

    private Playlist createPlaylist(Long userId, String name) {
        Playlist p = new Playlist();
        p.setUserId(userId); p.setName(name);
        return playlistRepository.save(p);
    }

    private PlaylistVideo addVideoToPlaylist(Long playlistId, Long videoId) {
        PlaylistVideo pv = new PlaylistVideo();
        pv.setPlaylistId(playlistId); pv.setVideoId(videoId);
        return playlistVideoRepository.save(pv);
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsUserPlaylistsNewestFirst() {
        List<Playlist> playlists = playlistRepository.findByUserIdOrderByCreatedAtDesc(1L);
        assertThat(playlists).extracting(Playlist::getName).containsExactly("new", "old");
        assertThat(playlists).extracting(Playlist::getUserId).containsOnly(1L);
    }

    @Test
    void findByUserId_isolatesUsers() {
        List<Playlist> user2 = playlistRepository.findByUserIdOrderByCreatedAtDesc(2L);
        assertThat(user2).extracting(Playlist::getName).containsExactly("other");
    }

    @Test
    void countByPlaylistId_returnsVideoCount() {
        assertThat(playlistVideoRepository.countByPlaylistId(user1PlaylistNewId)).isEqualTo(2L);
        assertThat(playlistVideoRepository.countByPlaylistId(user1PlaylistOldId)).isEqualTo(1L);
        assertThat(playlistVideoRepository.countByPlaylistId(user2PlaylistId)).isZero();
    }

    @Test
    void existsByPlaylistIdAndVideoId_returnsBoolean() {
        assertThat(playlistVideoRepository.existsByPlaylistIdAndVideoId(user1PlaylistNewId, 100L)).isTrue();
        assertThat(playlistVideoRepository.existsByPlaylistIdAndVideoId(user1PlaylistNewId, 999L)).isFalse();
    }

    @Test
    void findByPlaylistIdOrderByAddedAtDesc_newestFirst() {
        List<PlaylistVideo> videos = playlistVideoRepository.findByPlaylistIdOrderByAddedAtDesc(user1PlaylistNewId);
        assertThat(videos).extracting(PlaylistVideo::getVideoId).containsExactly(200L, 100L);
    }

    @Test
    void deleteByPlaylistId_removesAllChildren() {
        playlistVideoRepository.deleteByPlaylistId(user1PlaylistNewId);
        playlistVideoRepository.flush();

        assertThat(playlistVideoRepository.countByPlaylistId(user1PlaylistNewId)).isZero();
        assertThat(playlistVideoRepository.countByPlaylistId(user1PlaylistOldId)).isEqualTo(1L);
    }
}
