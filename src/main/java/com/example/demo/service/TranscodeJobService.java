package com.example.demo.service;

import com.example.demo.controller.WorkerController.TranscodeResult;
import com.example.demo.entity.TranscodeJob;
import com.example.demo.repository.TranscodeJobRepository;
import com.example.demo.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 트랜스코딩 작업 큐 관리. 서버는 enqueue로 작업을 쌓고, 로컬 GPU 워커가
 * claimNext로 가져가 처리한 뒤 complete/fail로 결과를 통보한다.
 */
@Service
public class TranscodeJobService {

    private static final Logger log = LoggerFactory.getLogger(TranscodeJobService.class);

    private final TranscodeJobRepository jobRepository;
    private final VideoRepository videoRepository;

    public TranscodeJobService(TranscodeJobRepository jobRepository, VideoRepository videoRepository) {
        this.jobRepository = jobRepository;
        this.videoRepository = videoRepository;
    }

    @Transactional
    public TranscodeJob enqueue(Long videoId, String uuid, String inputKey, boolean needThumbnail) {
        TranscodeJob job = new TranscodeJob();
        job.setVideoId(videoId);
        job.setUuid(uuid);
        job.setInputKey(inputKey);
        job.setNeedThumbnail(needThumbnail);
        job.setStatus("PENDING");
        TranscodeJob saved = jobRepository.save(job);
        log.info("[Transcode] 작업 큐 추가 jobId={} videoId={} inputKey={}", saved.getId(), videoId, inputKey);
        return saved;
    }

    /** 다음 PENDING 작업을 원자적으로 점유해 반환한다. 없으면 empty. */
    @Transactional
    public Optional<TranscodeJob> claimNext(String workerId) {
        Optional<TranscodeJob> opt = jobRepository.findFirstByStatusOrderByIdAsc("PENDING");
        if (opt.isEmpty()) return Optional.empty();

        TranscodeJob job = opt.get();
        int updated = jobRepository.claim(job.getId(), workerId, LocalDateTime.now());
        if (updated == 0) {
            // 다른 워커가 먼저 가져감 — 이번엔 빈 손으로 돌려보내고 다음 폴링에서 재시도
            return Optional.empty();
        }
        VideoUploadService.publishEncodeStatus(job.getVideoId(), "ENCODING");
        log.info("[Transcode] 작업 점유 jobId={} videoId={} worker={}", job.getId(), job.getVideoId(), workerId);
        return jobRepository.findById(job.getId());
    }

    @Transactional
    public void complete(Long jobId, TranscodeResult r) {
        TranscodeJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."));

        videoRepository.findById(job.getVideoId()).ifPresent(v -> {
            if (notBlank(r.mainUrl()))  v.setVideoUrl(r.mainUrl());
            if (notBlank(r.url1080()))  v.setVideoUrl1080(r.url1080());
            if (notBlank(r.url720()))   v.setVideoUrl720(r.url720());
            if (notBlank(r.url480()))   v.setVideoUrl480(r.url480());
            if (notBlank(r.url360()))   v.setVideoUrl360(r.url360());
            if (notBlank(r.thumbnailUrl()) && (v.getThumbnail() == null || v.getThumbnail().isBlank())) {
                v.setThumbnail(r.thumbnailUrl());
            }
            videoRepository.save(v);
        });

        job.setStatus("DONE");
        job.setErrorMessage(null);
        jobRepository.save(job);
        VideoUploadService.publishEncodeStatus(job.getVideoId(), "DONE");
        log.info("[Transcode] 작업 완료 jobId={} videoId={}", jobId, job.getVideoId());
    }

    @Transactional
    public void fail(Long jobId, String message) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("ERROR");
            job.setErrorMessage(message);
            jobRepository.save(job);
            VideoUploadService.publishEncodeStatus(job.getVideoId(), "ERROR:" + message);
            log.warn("[Transcode] 작업 실패 jobId={} videoId={} msg={}", jobId, job.getVideoId(), message);
        });
    }

    /** 점유된 채 3시간 넘게 멈춘 작업을 다시 PENDING으로 회수한다(워커 중단/크래시 대비). */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void reclaimStaleJobs() {
        LocalDateTime now = LocalDateTime.now();
        int n = jobRepository.reclaimStale(now.minusHours(3), now);
        if (n > 0) log.info("[Transcode] 멈춘 작업 {}개를 PENDING으로 회수했습니다.", n);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
