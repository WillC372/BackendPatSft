package com.aquasense.support;

import com.aquasense.config.AppProperties;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.measurement.validation.ValidationStatus;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.PhRange;
import com.aquasense.profile.ProfileType;
import com.aquasense.profile.WaterProfile;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

public final class TestData {

    private static final PhClassifier CLASSIFIER = new PhClassifier();

    private TestData() {
    }

    public static WaterProfile pool() {
        return new WaterProfile(null, "Piscina", ProfileType.POOL, new PhRange(7.2, 7.8, 7.0, 8.0), "Piscina", "CDC");
    }

    public static Measurement measurement(long id, double ph, Instant at, WaterProfile profile) {
        var c = CLASSIFIER.classify(ph, profile);
        Measurement m = new Measurement(1L, null, 1L, ph, null, at, MeasurementSource.MANUAL, c.nature(), c.status(),
            ValidationStatus.VALID, null, null);
        setId(m, id);
        return m;
    }

    public static AppProperties properties(String apiKey, int freeQuota) {
        return new AppProperties(
            new AppProperties.Security("test-secret-key-that-is-long-enough-for-hs256-signing", 8, 30, null, 10),
            new AppProperties.Cors(List.of("http://localhost:5500")),
            new AppProperties.Mail("test@aquasense.local", "http://localhost:5500/#/reset-password", false, null),
            new AppProperties.Ai(apiKey, "claude-opus-5-5", "low", 16000, freeQuota, 100, 10, java.util.List.of()),
            new AppProperties.Device(20, 3, true));
    }

    private static void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
