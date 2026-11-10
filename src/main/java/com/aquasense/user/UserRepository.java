package com.aquasense.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository: encapsulates access to user data; services do not write SQL.
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    /** Active administrators that are real people (the demonstration administrator does not count). */
    long countByRoleAndEnabledTrueAndDemoFalse(Role role);

    long countByPlan(Plan plan);

    long countByPlanAndPremiumUntilIsNull(Plan plan);

    long countByEnabledFalse();

    long countByCreatedAtAfter(Instant after);

    long countByLastLoginAtAfter(Instant after);
}
