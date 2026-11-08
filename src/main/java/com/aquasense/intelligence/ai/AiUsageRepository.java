package com.aquasense.intelligence.ai;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    long countByUserIdAndCachedFalseAndSuccessTrueAndCreatedAtAfter(Long userId, Instant after);

    List<AiUsage> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, Instant after);

    List<AiUsage> findByCreatedAtAfterOrderByCreatedAtAsc(Instant after);
}
