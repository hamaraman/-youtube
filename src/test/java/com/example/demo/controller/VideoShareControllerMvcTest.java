package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.service.VideoShareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;

@WebMvcTest(controllers = VideoShareController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class VideoShareControllerMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private VideoShareService videoShareService;

    @Test
    void sharePage_returnsHtml() throws Exception {
        when(videoShareService.generateShareHtml(eq(1L), any(), any()))
                .thenReturn("<html>ok</html>");

        mockMvc.perform(get("/share/video/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("<html>ok</html>"));
    }

    @Test
    void sharePage_passesTimestampToService() throws Exception {
        when(videoShareService.generateShareHtml(eq(1L), eq(42), any()))
                .thenReturn("<html>t</html>");

        mockMvc.perform(get("/share/video/1").param("t", "42"))
                .andExpect(status().isOk());
        verify(videoShareService).generateShareHtml(eq(1L), eq(42), any());
    }

    @Test
    void sharePage_buildsOriginFromRequestScheme() throws Exception {
        when(videoShareService.generateShareHtml(eq(1L), any(), eq("http://localhost")))
                .thenReturn("<html>ok</html>");

        mockMvc.perform(get("/share/video/1"))
                .andExpect(status().isOk());
        verify(videoShareService).generateShareHtml(eq(1L), any(), eq("http://localhost"));
    }

    @Test
    void sharePage_customPort_isIncluded() throws Exception {
        when(videoShareService.generateShareHtml(eq(1L), any(), eq("http://localhost:8081")))
                .thenReturn("<html>ok</html>");

        mockMvc.perform(get("http://localhost:8081/share/video/1"))
                .andExpect(status().isOk());
    }

    @Test
    void sharePage_notFound_returnsHtmlErrorPage() throws Exception {
        when(videoShareService.generateShareHtml(eq(99L), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        var result = mockMvc.perform(get("/share/video/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("영상을 찾을 수 없습니다.");
    }

    @Test
    void sharePage_privateVideo_returns404ErrorHtml() throws Exception {
        when(videoShareService.generateShareHtml(eq(1L), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "비공개 영상입니다."));

        var result = mockMvc.perform(get("/share/video/1"))
                .andExpect(status().isNotFound())
                .andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("비공개");
    }
}
