package com.example.demo.service;

import com.example.demo.entity.Video;
import com.example.demo.repository.VideoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VideoShareService {

    private final VideoRepository videoRepository;

    public VideoShareService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    public String generateShareHtml(Long id, Integer t, String origin) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        if ("비공개".equals(video.getVisibility())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "비공개 영상입니다.");
        }

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

        return "<!DOCTYPE html>\n"
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
