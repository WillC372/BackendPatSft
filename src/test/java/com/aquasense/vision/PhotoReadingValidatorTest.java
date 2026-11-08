package com.aquasense.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PhotoReadingValidatorTest {

    private final PhotoReadingValidator validator = new PhotoReadingValidator(new ObjectMapper());

    private static String json(String kind, boolean readable, double ph, double low, double high, double conf, boolean chart,
                               String issues, boolean tempVisible, double temp) {
        return String.format(java.util.Locale.US, """
            {"readable":%s,"kind":"%s","ph":%.2f,"phLow":%.2f,"phHigh":%.2f,"nearestReferenceValues":[],
             "confidence":%.2f,"referenceChartVisible":%s,"temperatureVisible":%s,"temperatureC":%.1f,
             "issues":[%s],"observations":"Comparé la zona reactiva con la carta."}""",
            readable, kind, ph, low, high, conf, chart, tempVisible, temp, issues);
    }

    @Test
    void stripWithChartGetsRealisticMarginAndCappedConfidence() {
        PhotoReading r = validator.validate(json("TEST_STRIP", true, 6.0, 6.0, 6.0, 0.95, true, "", false, 0), "Google Gemini", "g");
        assertThat(r.readable()).isTrue();
        assertThat(r.phLow()).isEqualTo(5.5);
        assertThat(r.phHigh()).isEqualTo(6.5);
        assertThat(r.confidence()).isEqualTo(0.7);
        assertThat(r.adjustments()).hasSize(2);
    }

    @Test
    void stripWithoutChartIsMarkedAndLimited() {
        PhotoReading r = validator.validate(json("TEST_STRIP", true, 7.0, 7.0, 7.0, 0.9, false, "\"GLARE\"", false, 0), "Groq", "q");
        assertThat(r.issues()).contains(PhotoIssue.NO_REFERENCE_CHART, PhotoIssue.GLARE);
        assertThat(r.phLow()).isEqualTo(6.0);
        assertThat(r.phHigh()).isEqualTo(8.0);
        assertThat(r.confidence()).isEqualTo(0.25);
    }

    @Test
    void meterDisplayKeepsDigitsAndTemperature() {
        PhotoReading r = validator.validate(json("METER_DISPLAY", true, 7.42, 7.42, 7.42, 0.99, false, "", true, 24.6), "Google Gemini", "g");
        assertThat(r.ph()).isEqualTo(7.42);
        assertThat(r.phLow()).isEqualTo(7.37);
        assertThat(r.confidence()).isEqualTo(0.9);
        assertThat(r.temperatureC()).isEqualTo(24.6);
        assertThat(r.issues()).doesNotContain(PhotoIssue.NO_REFERENCE_CHART);
    }

    @Test
    void unrelatedOrUnreadablePhotosProduceNoValue() {
        PhotoReading r = validator.validate(json("OTHER", false, 0, 0, 0, 0, false, "\"NOT_A_PH_TEST\"", false, 0), "Google Gemini", "g");
        assertThat(r.readable()).isFalse();
        assertThat(r.ph()).isNull();
        PhotoReading forced = validator.validate(json("OTHER", true, 7, 7, 7, 0.9, false, "", false, 0), "Google Gemini", "g");
        assertThat(forced.readable()).isFalse();
    }

    @Test
    void rejectsMalformedResponses() {
        assertThatThrownBy(() -> validator.validate("pH 7", "x", "y")).isInstanceOf(PhotoReadingValidator.InvalidPhotoReadingException.class);
        assertThatThrownBy(() -> validator.validate(json("TEST_STRIP", true, 7, 7, 7, 0.5, true, "", false, 0)
            .replace("TEST_STRIP", "CAMERA"), "x", "y")).isInstanceOf(PhotoReadingValidator.InvalidPhotoReadingException.class);
        assertThatThrownBy(() -> validator.validate(json("TEST_STRIP", true, 15, 7, 7, 0.5, true, "", false, 0), "x", "y"))
            .isInstanceOf(PhotoReadingValidator.InvalidPhotoReadingException.class);
    }
}
