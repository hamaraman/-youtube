package com.example.demo.service;

import com.example.demo.entity.Video;
import com.example.demo.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoShareServiceTest {

    @Mock private VideoRepository videoRepository;
    @InjectMocks private VideoShareService service;

    private Video video(String title, String desc, String thumbnail, String visibility) {
        Video v = new Video();
        v.setId(1L); v.setOwnerId(5L);
        v.setTitle(title); v.setDescription(desc);
        v.setChannel("MyChannel"); v.setThumbnail(thumbnail);
        v.setDuration("1:23"); v.setVisibility(visibility);
        return v;
    }

    @Test
    void videoMissing_throwsNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generateShareHtml(1L, null, "https://mytube.com"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void privateVideo_throwsNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video("t", "d", "th", "비공개")));
        assertThatThrownBy(() -> service.generateShareHtml(1L, null, "https://mytube.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("비공개");
    }

    @Test
    void publicVideo_containsOgTags() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(
                video("Hello World", "설명입니다", "/uploads/thumbnails/abc.png", "공개")));

        String html = service.generateShareHtml(1L, null, "https://mytube.com");

        assertThat(html).contains("<title>Hello World - MyTube</title>");
        assertThat(html).contains("og:title\" content=\"Hello World\"");
        assertThat(html).contains("og:description\" content=\"설명입니다\"");
        assertThat(html).contains("og:image\" content=\"https://mytube.com/uploads/thumbnails/abc.png\"");
        assertThat(html).contains("og:url\" content=\"https://mytube.com/share/video/1\"");
    }

    @Test
    void absoluteThumbnailUrl_isNotRewritten() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(
                video("t", "d", "https://cdn.example.com/img.png", "공개")));

        String html = service.generateShareHtml(1L, null, "https://mytube.com");

        assertThat(html).contains("og:image\" content=\"https://cdn.example.com/img.png\"");
    }

    @Test
    void emptyDescription_fallsBackToChannelAndDuration() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(
                video("t", "  ", "th", "공개")));

        String html = service.generateShareHtml(1L, null, "https://mytube.com");

        assertThat(html).contains("MyChannel · 1:23");
    }

    @Test
    void withTimestamp_addsTQueryParam() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video("t", "d", "th", "공개")));

        String html = service.generateShareHtml(1L, 42, "https://mytube.com");

        assertThat(html).contains("window.location.replace('https://mytube.com/watch.html?v=1&t=42')");
        assertThat(html).contains("og:url\" content=\"https://mytube.com/share/video/1?t=42\"");
    }

    @Test
    void zeroTimestamp_isTreatedAsNoTimestamp() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video("t", "d", "th", "공개")));

        String html = service.generateShareHtml(1L, 0, "https://mytube.com");

        assertThat(html).doesNotContain("&t=0");
        assertThat(html).doesNotContain("?t=0");
    }

    @Test
    void htmlSpecialCharsInTitle_areEscaped() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(
                video("<script>alert('xss')</script>", "d", "th", "공개")));

        String html = service.generateShareHtml(1L, null, "https://mytube.com");

        assertThat(html).doesNotContain("<script>alert");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).contains("&#39;xss&#39;");
    }

    @Test
    void longDescription_isTruncated() {
        String longDesc = "가".repeat(300);
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video("t", longDesc, "th", "공개")));

        String html = service.generateShareHtml(1L, null, "https://mytube.com");

        assertThat(html).contains("…");
    }
}
