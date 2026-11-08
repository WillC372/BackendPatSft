package com.aquasense.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquasense.support.TestData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class MeasurementIteratorTest {

    @Test
    void walksAllPagesLazily() {
        MeasurementRepository repository = mock(MeasurementRepository.class);
        List<Measurement> all = IntStream.range(0, 1200)
            .mapToObj(i -> TestData.measurement(i, 7.0, Instant.now(), TestData.pool())).toList();
        when(repository.findByUserIdAndMeasuredAtBetween(eq(1L), any(), any(), any(Pageable.class))).thenAnswer(inv -> {
            Pageable page = inv.getArgument(3);
            int from = (int) page.getOffset();
            int to = Math.min(from + page.getPageSize(), all.size());
            return new PageImpl<>(all.subList(from, to), PageRequest.of(page.getPageNumber(), page.getPageSize()), all.size());
        });
        MeasurementIterator iterator = new MeasurementIterator(repository, 1L, Instant.EPOCH, Instant.now());
        List<Measurement> collected = new ArrayList<>();
        iterator.forEachRemaining(collected::add);
        assertThat(collected).hasSize(1200);
        assertThat(collected.get(1199).getId()).isEqualTo(1199L);
    }
}
