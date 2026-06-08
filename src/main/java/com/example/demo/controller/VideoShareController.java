package com.example.demo.controller;

import com.example.demo.entity.Video;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequestMapping("/share/video")
public class VideoShareController {

    private final VideoRepository videoRepository;

    public VideoShareController(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> sharePage(
            @PathVariable Long id,
            @RequestParam(required = false) Integer t,
            HttpServletRequest request) {

        Optional<Video> opt = videoRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body>영상을 찾을 수 없습니다.</body></html>");
        }

        Video video = opt.get();
        if ("비공개".equals(video.getVisibility())) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body>비공개 영상입니다.</body></html>");
        }

        String origin = buildOrigin(request);
        String watchUrl = origin + "/watch.html?v=" + id + (t != null && t > 0 ? "&t=" + t : "");
        String shareUrl = origin + "/share/video/" + id + (t != null && t > 0 ? "?t=" + t : "");

        String thumbnail = video.getThumbnail();
        if (thumbnail != null && !thumbnail.startsWith("http")) {
            thumbnail = origin + thumbnail;
        }

        String channel = video.getChannel() != null ? video.getChannel() : "MyTube";
        String descRaw = video.getDescription();
        String desc = (descRaw != null && !descRaw.isBlank())
                ? truncate(descRaw, 200)
                : channel + " · " + video.getDuration();

        String title = e(video.getTitle()) + " - MyTube";
        String ogTitle = e(video.getTitle());
        String ogDesc = e(desc);
        String ogImage = e(thumbnail != null ? thumbnail : "");
        String ogUrl = e(shareUrl);
        String jsRedirect = watchUrl.replace("\\", "\\\\").replace("'", "\\'");

        String html = "<!DOCTYPE html>\n"
                + "<html lang=\"ko\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\" />\n"
                + "  <title>" + title + "</title>\n"
                + "  <meta property=\"og:type\" content=\"video.other\" />\n"
                + "  <meta property=\"og:title\" content=\"" + ogTitle + "\" />\n"
                + "  <meta property=\"og:description\" content=\"" + ogDesc + "\" />\n"
                + "  <meta property=\"og:image\" content=\"" + ogImage + "\" />\n"
                + "  <meta property=\"og:image:width\" content=\"1280\" />\n"
                + "  <meta property=\"og:image:height\" content=\"720\" />\n"
                + "  <meta property=\"og:url\" content=\"" + ogUrl + "\" />\n"
                + "  <meta property=\"og:site_name\" content=\"MyTube\" />\n"
                + "  <meta name=\"twitter:card\" content=\"summary_large_image\" />\n"
                + "  <meta name=\"twitter:title\" content=\"" + ogTitle + "\" />\n"
                + "  <meta name=\"twitter:description\" content=\"" + ogDesc + "\" />\n"
                + "  <meta name=\"twitter:image\" content=\"" + ogImage + "\" />\n"
                + "</head>\n"
                + "<body>\n"
                + "  <script>window.location.replace('" + jsRedirect + "');</script>\n"
                + "</body>\n"
                + "</html>";

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private static String buildOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static String e(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
