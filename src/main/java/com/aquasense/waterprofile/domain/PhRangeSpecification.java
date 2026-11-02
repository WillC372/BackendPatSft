package com.aquasense.waterprofile.domain;

import com.aquasense.shared.spec.Specification;
import java.math.BigDecimal;

public final class PhRangeSpecification implements Specification<BigDecimal> {

    private final BigDecimal min;
    private final BigDecimal max;

    public PhRangeSpecification(BigDecimal min, BigDecimal max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean isSatisfiedBy(BigDecimal ph) {
        return ph.compareTo(min) >= 0 && ph.compareTo(max) <= 0;
    }
}
