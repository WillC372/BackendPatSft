package com.aquasense.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceImportRepository extends JpaRepository<ReferenceImport, Long> {

    Optional<ReferenceImport> findTopByOrderByStartedAtDesc();

    Optional<ReferenceImport> findTopBySuccessTrueOrderByStartedAtDesc();
}
