package com.aquasense.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndPurpose(String tokenHash, PasswordResetToken.Purpose purpose);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from PasswordResetToken t where t.expiresAt < :before or t.usedAt < :before")
    int deleteFinishedBefore(@org.springframework.data.repository.query.Param("before") java.time.Instant before);

    java.util.List<PasswordResetToken> findByUserIdAndPurposeAndUsedAtIsNull(Long userId, PasswordResetToken.Purpose purpose);
}
