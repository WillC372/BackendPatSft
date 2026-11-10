package com.aquasense.reference;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceStationRepository extends JpaRepository<ReferenceStation, String> {

    List<ReferenceStation> findByFeaturedTrueOrderByNameAsc();

    List<ReferenceStation> findByLastTimeAfter(Instant after);
}
