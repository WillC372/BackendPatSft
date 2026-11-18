package com.aquaassistant.measurement;

import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Iterator: walks a user's history page by page.
 * The CSV export and the reports process thousands of measurements without loading them all in memory.
 */
public class MeasurementIterator implements Iterator<Measurement> {

    private static final int PAGE_SIZE = 500;

    private final MeasurementRepository repository;
    private final Long placeId;
    private final Instant from;
    private final Instant to;
    private int pageNumber;
    private Iterator<Measurement> current;
    private boolean lastPage;

    public MeasurementIterator(MeasurementRepository repository, Long placeId, Instant from, Instant to) {
        this.repository = repository;
        this.placeId = placeId;
        this.from = from;
        this.to = to;
        loadPage();
    }

    private void loadPage() {
        Page<Measurement> page = repository.findByPlaceIdAndMeasuredAtBetween(placeId, from, to,
            PageRequest.of(pageNumber, PAGE_SIZE, Sort.by("measuredAt").ascending().and(Sort.by("id"))));
        current = page.getContent().iterator();
        lastPage = page.isLast();
        pageNumber++;
    }

    @Override
    public boolean hasNext() {
        if (current.hasNext()) {
            return true;
        }
        if (lastPage) {
            return false;
        }
        loadPage();
        return current.hasNext();
    }

    @Override
    public Measurement next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return current.next();
    }
}
