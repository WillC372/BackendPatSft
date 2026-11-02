package com.aquasense.billing.repository;

import com.aquasense.billing.domain.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByOwnerId(UUID ownerId);
}
