package com.example.demo.service;

import com.example.demo.config.DataInitializer;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoReport;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoReportRepository;
import com.example.demo.repository.VideoRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private VideoRepository videoRepository;
    @Mock private UserRepository userRepository;
    @Mock private VideoReportRepository videoReportRepository;
    @Mock private DataInitializer dataInitializer;

    @InjectMocks private AdminService adminService;

    private Video video(Long id, Long ownerId, String title, String visibility) {
        Video v = new Video();
        v.setId(id); v.setOwnerId(ownerId); v.setTitle(title);
        v.setChannel("ch"); v.setThumbnail("th"); v.setDuration("1:00");
        v.setVisibility(visibility);
        return v;
    }

    private User user(Long id, String username, String role) {
        User u = new User();
        u.setId(id); u.setUsername(username); u.setNickname("n" + id);
        u.setRole(role);
        return u;
    }

    @Test
    void listReports_enrichesWithVideoTitleAndReporter() {
        VideoReport r = new VideoReport();
        r.setId(7L); r.setVideoId(1L); r.setReporterId(10L);
        r.setReason("스팸"); r.setDetail("광고임");
        when(videoReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r));
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video(1L, 5L, "제목", "공개")));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "reporter", "USER")));

        List<Map<String, Object>> result = adminService.listReports();

        assertThat(result).hasSize(1);
        Map<String, Object> m = result.get(0);
        assertThat(m.get("videoTitle")).isEqualTo("제목");
        assertThat(m.get("reporter")).isEqualTo("n10");
        assertThat(m.get("reason")).isEqualTo("스팸");
        assertThat(m.get("detail")).isEqualTo("광고임");
    }

    @Test
    void listReports_deletedVideoAndUser_showFallbackLabels() {
        VideoReport r = new VideoReport();
        r.setId(8L); r.setVideoId(99L); r.setReporterId(88L);
        r.setReason("기타"); r.setDetail(null);
        when(videoReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r));
        when(videoRepository.findById(99L)).thenReturn(Optional.empty());
        when(userRepository.findById(88L)).thenReturn(Optional.empty());

        List<Map<String, Object>> result = adminService.listReports();

        Map<String, Object> m = result.get(0);
        assertThat(m.get("videoTitle")).isEqualTo("(삭제된 영상)");
        assertThat(m.get("reporter")).isEqualTo("(탈퇴한 사용자)");
        assertThat(m.get("detail")).isEqualTo("");
    }

    @Nested
    class SearchAndList {

        @Test
        void searchVideos_mapsFields() {
            when(videoRepository.findByTitleContaining("kw"))
                    .thenReturn(List.of(video(1L, 5L, "hello kw", "공개")));

            List<Map<String, Object>> results = adminService.searchVideos("kw");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).get("id")).isEqualTo(1L);
            assertThat(results.get(0).get("title")).isEqualTo("hello kw");
        }

        @Test
        void searchVideos_nullOwnerId_mappedToEmptyString() {
            when(videoRepository.findByTitleContaining("kw"))
                    .thenReturn(List.of(video(1L, null, "t", "공개")));

            List<Map<String, Object>> results = adminService.searchVideos("kw");

            assertThat(results.get(0).get("ownerId")).isEqualTo("");
        }

        @Test
        void listVideos_sortedByIdDesc() {
            when(videoRepository.findAll()).thenReturn(List.of(
                    video(1L, 5L, "a", "공개"),
                    video(3L, 5L, "c", "공개"),
                    video(2L, 5L, "b", "공개")));

            List<Map<String, Object>> results = adminService.listVideos();

            assertThat(results).extracting(m -> (Long) m.get("id"))
                    .containsExactly(3L, 2L, 1L);
        }
    }

    @Nested
    class DeleteVideo {

        @Test
        void notExists_throwsNotFound() {
            when(videoRepository.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> adminService.deleteVideo(99L))
                    .isInstanceOf(ResponseStatusException.class);
            verify(dataInitializer, never()).deleteVideoAndRelated(any());
        }

        @Test
        void exists_delegatesToDataInitializer() {
            when(videoRepository.existsById(1L)).thenReturn(true);

            adminService.deleteVideo(1L);

            verify(dataInitializer).deleteVideoAndRelated(1L);
        }
    }

    @Nested
    class BulkDelete {

        @Test
        void skipsMissingIdsAndReturnsCount() {
            when(videoRepository.existsById(1L)).thenReturn(true);
            when(videoRepository.existsById(2L)).thenReturn(false);
            when(videoRepository.existsById(3L)).thenReturn(true);

            int deleted = adminService.bulkDeleteVideos(List.of(1L, 2L, 3L));

            assertThat(deleted).isEqualTo(2);
            verify(dataInitializer).deleteVideoAndRelated(1L);
            verify(dataInitializer, never()).deleteVideoAndRelated(2L);
            verify(dataInitializer).deleteVideoAndRelated(3L);
        }
    }

    @Nested
    class ListBrokenVideos {

        @Test
        void nullVideoUrlAndEmbedUrl_isBroken() {
            ReflectionTestUtils.setField(adminService, "videoDir", "uploads/videos");
            Video broken = video(1L, 5L, "broken", "공개");
            broken.setVideoUrl(null); broken.setEmbedUrl(null);
            Video ok = video(2L, 5L, "ok", "공개");
            ok.setEmbedUrl("https://youtube.com/embed/abc");
            when(videoRepository.findAll()).thenReturn(List.of(broken, ok));

            List<Map<String, Object>> results = adminService.listBrokenVideos();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).get("id")).isEqualTo(1L);
            assertThat(results.get(0).get("reason")).isEqualTo("영상 소스 없음");
        }

        @Test
        void missingLocalFile_isBroken() {
            ReflectionTestUtils.setField(adminService, "videoDir", "uploads/videos");
            Video v = video(1L, 5L, "t", "공개");
            v.setVideoUrl("/uploads/videos/does-not-exist-xyz.mp4");
            when(videoRepository.findAll()).thenReturn(List.of(v));

            List<Map<String, Object>> results = adminService.listBrokenVideos();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).get("reason")).isEqualTo("파일 없음 (로컬)");
        }
    }

    @Nested
    class ListUsers {

        @Test
        void mapsAllFields_withDefaults() {
            User u = user(1L, "alice", null);
            u.setNickname(null); u.setChannelName(null); u.setEmail(null);
            when(userRepository.findAll()).thenReturn(List.of(u));

            List<Map<String, Object>> results = adminService.listUsers();

            assertThat(results.get(0).get("username")).isEqualTo("alice");
            assertThat(results.get(0).get("nickname")).isEqualTo("");
            assertThat(results.get(0).get("role")).isEqualTo("USER");
        }
    }

    @Nested
    class DeleteUser {

        @Test
        void deletingSelf_throwsBadRequest() {
            assertThatThrownBy(() -> adminService.deleteUser(1L, 1L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("자기 자신");
        }

        @Test
        void missing_throwsNotFound() {
            when(userRepository.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> adminService.deleteUser(99L, 1L))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void deletesUsersVideosThenUser() {
            when(userRepository.existsById(2L)).thenReturn(true);
            when(videoRepository.findAll()).thenReturn(List.of(
                    video(10L, 2L, "target", "공개"),
                    video(11L, 3L, "other", "공개")));

            adminService.deleteUser(2L, 1L);

            verify(dataInitializer).deleteVideoAndRelated(10L);
            verify(dataInitializer, never()).deleteVideoAndRelated(11L);
            verify(userRepository).deleteById(2L);
        }
    }

    @Nested
    class SetUserRole {

        @Test
        void invalidRole_throwsBadRequest() {
            assertThatThrownBy(() -> adminService.setUserRole(1L, "MODERATOR"))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void missingUser_throwsNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> adminService.setUserRole(99L, "admin"))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void lowerCase_isNormalizedAndSaved() {
            User u = user(1L, "alice", "USER");
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));
            when(userRepository.save(u)).thenReturn(u);

            adminService.setUserRole(1L, "admin");

            assertThat(u.getRole()).isEqualTo("ADMIN");
            verify(userRepository).save(u);
        }
    }
}
