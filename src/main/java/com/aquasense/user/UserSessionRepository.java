package com.aquasense.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Page<UserSession> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    Optional<UserSession> findByIdAndUserId(Long id, Long userId);

    List<UserSession> findByUserIdAndEndedAtIsNull(Long userId);
}
