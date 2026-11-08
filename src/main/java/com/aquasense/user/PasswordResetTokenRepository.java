package com.aquasense.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndPurpose(String tokenHash, PasswordResetToken.Purpose purpose);

    java.util.List<PasswordResetToken> findByUserIdAndPurposeAndUsedAtIsNull(Long userId, PasswordResetToken.Purpose purpose);
}
