package com.aquasense.measurement;

import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Iterator: recorre el historial de un usuario pagina por pagina.
 * La exportación CSV y los informes procesan miles de mediciones sin cargarlas todas en memoria.
 */
public class MeasurementIterator implements Iterator<Measurement> {

    private static final int PAGE_SIZE = 500;

    private final MeasurementRepository repository;
    private final Long userId;
    private final Instant from;
    private final Instant to;
    private int pageNumber;
    private Iterator<Measurement> current;
    private boolean lastPage;

    public MeasurementIterator(MeasurementRepository repository, Long userId, Instant from, Instant to) {
        this.repository = repository;
        this.userId = userId;
        this.from = from;
        this.to = to;
        loadPage();
    }

    private void loadPage() {
        Page<Measurement> page = repository.findByUserIdAndMeasuredAtBetween(userId, from, to,
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
