package com.example.demo.service;

import com.example.demo.entity.Playlist;
import com.example.demo.entity.PlaylistVideo;
import com.example.demo.entity.Video;
import com.example.demo.repository.PlaylistRepository;
import com.example.demo.repository.PlaylistVideoRepository;
import com.example.demo.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock private PlaylistRepository playlistRepository;
    @Mock private PlaylistVideoRepository playlistVideoRepository;
    @Mock private VideoRepository videoRepository;

    @InjectMocks private PlaylistService playlistService;

    private Playlist ownedPlaylist(Long id, Long userId, String name) {
        Playlist p = new Playlist();
        p.setId(id); p.setUserId(userId); p.setName(name);
        return p;
    }

    @Test
    void createPlaylist_blankName_throwsBadRequest() {
        assertThatThrownBy(() -> playlistService.createPlaylist("   ", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이름");
        assertThatThrownBy(() -> playlistService.createPlaylist(null, 1L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPlaylist_valid_savesTrimmedName() {
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(inv -> {
            Playlist p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        Map<String, Object> result = playlistService.createPlaylist("  나만의 목록  ", 1L);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("name")).isEqualTo("나만의 목록");
        ArgumentCaptor<Playlist> captor = ArgumentCaptor.forClass(Playlist.class);
        verify(playlistRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getName()).isEqualTo("나만의 목록");
    }

    @Test
    void deletePlaylist_missing_throwsNotFound() {
        when(playlistRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> playlistService.deletePlaylist(99L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("찾을 수 없");
    }

    @Test
    void deletePlaylist_wrongOwner_throwsForbidden() {
        Playlist p = ownedPlaylist(1L, 2L, "n");
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> playlistService.deletePlaylist(1L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("권한");
        verify(playlistRepository, never()).delete(any());
    }

    @Test
    void deletePlaylist_owner_deletesChildrenThenPlaylist() {
        Playlist p = ownedPlaylist(1L, 2L, "n");
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(p));

        playlistService.deletePlaylist(1L, 2L);

        verify(playlistVideoRepository).deleteByPlaylistId(1L);
        verify(playlistRepository).delete(p);
    }

    @Test
    void renamePlaylist_wrongOwner_throwsForbidden() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "old")));
        assertThatThrownBy(() -> playlistService.renamePlaylist(1L, "new", 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("권한");
    }

    @Test
    void renamePlaylist_blankName_throwsBadRequest() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "old")));
        assertThatThrownBy(() -> playlistService.renamePlaylist(1L, "  ", 2L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void renamePlaylist_valid_updatesName() {
        Playlist p = ownedPlaylist(1L, 2L, "old");
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(p));

        playlistService.renamePlaylist(1L, "  new name  ", 2L);

        assertThat(p.getName()).isEqualTo("new name");
        verify(playlistRepository).save(p);
    }

    @Test
    void addVideo_wrongOwner_throwsForbidden() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        assertThatThrownBy(() -> playlistService.addVideo(1L, 5L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("권한");
    }

    @Test
    void addVideo_missingVideo_throwsBadRequest() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        when(videoRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> playlistService.addVideo(1L, 5L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("영상");
    }

    @Test
    void addVideo_nullVideoId_throwsBadRequest() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        assertThatThrownBy(() -> playlistService.addVideo(1L, null, 2L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addVideo_duplicate_returnsSuccessFalseWithoutSaving() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        when(videoRepository.findById(5L)).thenReturn(Optional.of(new Video()));
        when(playlistVideoRepository.existsByPlaylistIdAndVideoId(1L, 5L)).thenReturn(true);

        Map<String, Object> result = playlistService.addVideo(1L, 5L, 2L);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).asString().contains("이미");
        verify(playlistVideoRepository, never()).save(any());
    }

    @Test
    void addVideo_new_savesEntry() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        when(videoRepository.findById(5L)).thenReturn(Optional.of(new Video()));
        when(playlistVideoRepository.existsByPlaylistIdAndVideoId(1L, 5L)).thenReturn(false);

        Map<String, Object> result = playlistService.addVideo(1L, 5L, 2L);

        assertThat(result.get("success")).isEqualTo(true);
        ArgumentCaptor<PlaylistVideo> captor = ArgumentCaptor.forClass(PlaylistVideo.class);
        verify(playlistVideoRepository).save(captor.capture());
        assertThat(captor.getValue().getPlaylistId()).isEqualTo(1L);
        assertThat(captor.getValue().getVideoId()).isEqualTo(5L);
    }

    @Test
    void removeVideo_wrongOwner_throwsForbidden() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        assertThatThrownBy(() -> playlistService.removeVideo(1L, 5L, 999L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void removeVideo_existing_deletes() {
        PlaylistVideo pv = new PlaylistVideo();
        pv.setId(77L); pv.setPlaylistId(1L); pv.setVideoId(5L);

        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        when(playlistVideoRepository.findByPlaylistIdAndVideoId(1L, 5L)).thenReturn(Optional.of(pv));

        playlistService.removeVideo(1L, 5L, 2L);

        verify(playlistVideoRepository).delete(pv);
    }

    @Test
    void removeVideo_missingEntry_isNoop() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(ownedPlaylist(1L, 2L, "n")));
        when(playlistVideoRepository.findByPlaylistIdAndVideoId(1L, 5L)).thenReturn(Optional.empty());

        playlistService.removeVideo(1L, 5L, 2L);

        verify(playlistVideoRepository, never()).delete(any());
    }

    @Test
    void getPlaylistDetails_missing_throwsNotFound() {
        when(playlistRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> playlistService.getPlaylistDetails(99L, 1L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getPlaylistDetails_ownerFlagReflectsCaller() {
        Playlist p = ownedPlaylist(1L, 2L, "이름");
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(p));
        when(playlistVideoRepository.findByPlaylistIdOrderByAddedAtDesc(1L)).thenReturn(List.of());

        assertThat(playlistService.getPlaylistDetails(1L, 2L).get("isOwner")).isEqualTo(true);
        assertThat(playlistService.getPlaylistDetails(1L, 999L).get("isOwner")).isEqualTo(false);
        assertThat(playlistService.getPlaylistDetails(1L, null).get("isOwner")).isEqualTo(false);
    }
}
