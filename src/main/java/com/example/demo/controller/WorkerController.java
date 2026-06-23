package com.example.demo.controller;

import com.example.demo.entity.TranscodeJob;
import com.example.demo.service.TranscodeJobService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 로컬 GPU 워커 전용 API. 세션/JWT가 아닌 공유 토큰(X-Worker-Token 헤더)으로 인증한다.
 * 워커는 claim으로 작업을 가져가 MinIO에서 원본을 직접 받아 변환하고, 결과 URL을 result로 통보한다.
 */
@RestController
@RequestMapping("/api/worker")
public class WorkerController {

    private final TranscodeJobService jobService;
    private final String workerToken;

    public WorkerController(TranscodeJobService jobService,
                           @Value("${worker.token:}") String workerToken) {
        this.jobService = jobService;
        this.workerToken = workerToken;
    }

    private boolean authed(HttpServletRequest req) {
        if (workerToken == null || workerToken.isBlank()) return false;
        String t = req.getHeader("X-Worker-Token");
        return workerToken.equals(t);
    }

    /** 다음 작업을 점유해 반환. 작업이 없으면 204, 인증 실패 시 401. */
    @PostMapping("/jobs/claim")
    public ResponseEntity<?> claim(@RequestParam(value = "workerId", required = false) String workerId,
                                   HttpServletRequest req) {
        if (!authed(req)) return ResponseEntity.status(401).build();
        String id = (workerId == null || workerId.isBlank()) ? "unknown" : workerId;
        return jobService.claimNext(id)
                .<ResponseEntity<?>>map(this::toClaimResponse)
                .orElse(ResponseEntity.noContent().build());
    }

    private ResponseEntity<?> toClaimResponse(TranscodeJob job) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("jobId", job.getId());
        body.put("videoId", job.getVideoId());
        body.put("uuid", job.getUuid());
        body.put("inputKey", job.getInputKey());
        body.put("needThumbnail", job.isNeedThumbnail());
        return ResponseEntity.ok(body);
    }

    /** 변환 완료 결과(MinIO 공개 URL들) 통보. 워커가 MinIO에 직접 업로드한 뒤 URL만 전달한다. */
    @PostMapping("/jobs/{id}/result")
    public ResponseEntity<?> result(@PathVariable Long id,
                                    @RequestBody TranscodeResult body,
                                    HttpServletRequest req) {
        if (!authed(req)) return ResponseEntity.status(401).build();
        jobService.complete(id, body);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 변환 실패 통보. */
    @PostMapping("/jobs/{id}/error")
    public ResponseEntity<?> error(@PathVariable Long id,
                                   @RequestBody Map<String, String> body,
                                   HttpServletRequest req) {
        if (!authed(req)) return ResponseEntity.status(401).build();
        jobService.fail(id, body.getOrDefault("message", "unknown"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 워커가 MinIO에 업로드한 결과물의 공개 URL 모음. */
    public record TranscodeResult(
            String mainUrl,
            String url1080,
            String url720,
            String url480,
            String url360,
            String thumbnailUrl
    ) {}
}
