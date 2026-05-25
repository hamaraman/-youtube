package com.example.demo.config;

import com.example.demo.controller.AuthController;
import com.example.demo.entity.Video;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(20)
public class VideoAccessFilter extends OncePerRequestFilter {

    private final VideoRepository videoRepository;

    public VideoAccessFilter(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/uploads/videos/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Video> videoOpt = videoRepository.findByVideoUrl(path);
        if (videoOpt.isPresent()) {
            Video video = videoOpt.get();
            if ("비공개".equals(video.getVisibility())) {
                Long loginUserId = getLoginUserId(request);
                if (loginUserId == null || !loginUserId.equals(video.getOwnerId())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"비공개 영상입니다.\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Long getLoginUserId(HttpServletRequest request) {
        Object jwtUser = request.getAttribute("jwtLoginUser");
        if (jwtUser instanceof AuthController.SessionUser su) return su.getId();

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionUser = session.getAttribute("loginUser");
            if (sessionUser instanceof AuthController.SessionUser su) return su.getId();
        }
        return null;
    }
}
