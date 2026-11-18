package com.aquaassistant.reminder;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByUserIdOrderByNextDueAsc(Long userId);

    List<Reminder> findTop200ByActiveTrueAndNextDueBeforeOrderByNextDueAsc(Instant now);

    long countByUserId(Long userId);
}
