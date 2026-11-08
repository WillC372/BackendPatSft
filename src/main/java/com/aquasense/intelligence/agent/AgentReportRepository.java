package com.aquasense.intelligence.agent;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentReportRepository extends JpaRepository<AgentReportRecord, Long> {

    List<AgentReportRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
