package com.aquasense.measurement;

import com.aquasense.profile.PhStatus;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification: filtros del historial combinables (propietario, periodo, origen, estado)
 * sin escribir una consulta distinta por cada combinacion.
 */
public final class MeasurementSpecifications {

    private MeasurementSpecifications() {
    }

    public static Specification<Measurement> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Measurement> measuredFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("measuredAt"), from);
    }

    public static Specification<Measurement> measuredUntil(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("measuredAt"), to);
    }

    public static Specification<Measurement> fromSource(MeasurementSource source) {
        return (root, query, cb) -> source == null ? null : cb.equal(root.get("source"), source);
    }

    public static Specification<Measurement> withStatus(PhStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
