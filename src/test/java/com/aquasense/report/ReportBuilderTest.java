package com.aquasense.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportBuilderTest {

    @Test
    void buildsReportSkippingEmptySections() {
        Instant end = Instant.now();
        Report report = new ReportBuilder().title("Informe").period(end.minus(Duration.ofDays(7)), end)
            .profile("Piscina").measurementCount(3)
            .section("summary", "Resumen", List.of("3 mediciones"))
            .section("alerts", "Alertas", List.of())
            .disclaimer("aviso").build();
        assertThat(report.sections()).extracting(ReportSection::key).containsExactly("summary");
        assertThat(report.measurementCount()).isEqualTo(3);
    }

    @Test
    void requiresMandatoryFields() {
        assertThatThrownBy(() -> new ReportBuilder().title("x").build()).isInstanceOf(NullPointerException.class);
        Instant now = Instant.now();
        assertThatThrownBy(() -> new ReportBuilder().title("x").profile("p").period(now, now.minusSeconds(1)).build())
            .isInstanceOf(IllegalStateException.class);
    }
}
