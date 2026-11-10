package com.aquasense.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceImportRepository extends JpaRepository<ReferenceImport, Long> {

    Optional<ReferenceImport> findTopByOrderByStartedAtDesc();

    /** Last import that already finished (an import in progress has no result yet). */
    Optional<ReferenceImport> findTopByFinishedAtIsNotNullOrderByStartedAtDesc();

    Optional<ReferenceImport> findTopBySuccessTrueOrderByStartedAtDesc();
}
