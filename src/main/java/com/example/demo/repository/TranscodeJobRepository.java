package com.example.demo.repository;

import com.example.demo.entity.TranscodeJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TranscodeJobRepository extends JpaRepository<TranscodeJob, Long> {

    Optional<TranscodeJob> findFirstByStatusOrderByIdAsc(String status);

    List<TranscodeJob> findByStatus(String status);

    /**
     * 작업을 원자적으로 점유한다. status가 아직 PENDING일 때만 CLAIMED로 바꾸므로
     * 여러 워커가 동시에 같은 작업을 가져가는 것을 막는다. 변경된 행 수를 반환한다(0이면 이미 다른 워커가 가져감).
     */
    @Modifying
    @Query("UPDATE TranscodeJob j SET j.status = 'CLAIMED', j.workerId = :workerId, " +
           "j.claimedAt = :now, j.attempts = j.attempts + 1, j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status = 'PENDING'")
    int claim(@Param("id") Long id, @Param("workerId") String workerId, @Param("now") LocalDateTime now);

    /** 점유된 채 오래 멈춰 있는 작업을 다시 PENDING으로 돌려 재처리 가능하게 한다. */
    @Modifying
    @Query("UPDATE TranscodeJob j SET j.status = 'PENDING', j.workerId = null, j.updatedAt = :now " +
           "WHERE j.status = 'CLAIMED' AND j.claimedAt < :cutoff")
    int reclaimStale(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
