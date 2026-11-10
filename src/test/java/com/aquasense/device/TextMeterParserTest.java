package com.aquasense.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.device.hardware.PhReading;
import com.aquasense.device.hardware.TextMeterParser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Lines that laboratory meters send over USB or RS-232. */
class TextMeterParserTest {

    private static PhReading ok(String line) {
        Optional<PhReading> r = TextMeterParser.parse(line);
        assertThat(r).as(line).isPresent();
        return r.get();
    }

    @Test
    void readsLabelledAndBareValues() {
        assertThat(ok("pH 7.02  25.0 °C").ph()).isEqualTo(7.02);
        assertThat(ok("pH 7.02  25.0 °C").temperatureC()).isEqualTo(25.0);
        assertThat(ok("7.02 pH").ph()).isEqualTo(7.02);
        assertThat(ok("PH=6.98,T=24.8C").ph()).isEqualTo(6.98);
        assertThat(ok("PH=6.98,T=24.8C").temperatureC()).isEqualTo(24.8);
        assertThat(ok("PH:7.12;T:24.5").ph()).isEqualTo(7.12);
        assertThat(ok("07.40").ph()).isEqualTo(7.4);
        assertThat(ok("pH: 6,85").ph()).isEqualTo(6.85);
        assertThat(ok("  8.1 pH   21 C").temperatureC()).isEqualTo(21.0);
    }

    @Test
    void ignoresLinesWithoutAClearPh() {
        assertThat(TextMeterParser.parse("")).isEmpty();
        assertThat(TextMeterParser.parse("HANNA HI2211 v1.2")).isEmpty();
        assertThat(TextMeterParser.parse("mV 152.3")).isEmpty();
        assertThat(TextMeterParser.parse("12 34")).isEmpty();
        assertThat(TextMeterParser.parse("pH 15.2")).isEmpty();
        assertThat(TextMeterParser.parse("*OK")).isEmpty();
    }
}
