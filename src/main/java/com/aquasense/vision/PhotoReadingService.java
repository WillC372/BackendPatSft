package com.aquasense.vision;

import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.ai.AiAnswer;
import com.aquasense.intelligence.ai.AiImage;
import com.aquasense.intelligence.ai.AiPrompt;
import com.aquasense.intelligence.ai.AiProviderProxy;
import com.aquasense.intelligence.ai.AiTextProvider;
import com.aquasense.intelligence.ai.AiUnavailableException;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.measurement.dto.NewReading;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * pH reading from a photo (test strip or meter display):
 * 1) sanitizes the image, 2) the vision AI answers structured JSON, 3) it is adjusted to the real precision of the method,
 * 4) for strips a second opinion is requested from another provider and both are compared, 5) the user confirms or corrects before saving.
 */
@Service
public class PhotoReadingService {

    static final double AGREEMENT_TOLERANCE = 0.5;
    private static final Duration PENDING_TTL = Duration.ofMinutes(15);
    private static final String DISCLAIMER = "Valor estimado por IA a partir de una foto. Las tiras reactivas tienen una "
        + "precisión aproximada de ±0,5; para decisiones importantes usa un medidor calibrado.";
    static final String SYSTEM_PROMPT = """
        You are the photo reader of AquaSense AI. You receive the photo of a water pH test and answer only with the JSON given.
        1. Identify the type: TEST_STRIP (strip or indicator paper), METER_DISPLAY (display of a meter) or OTHER (anything else).
        2. TEST_STRIP: compare the color of the reactive pad with the color chart that appears in the photo.
           Put in nearestReferenceValues the most similar chart values (one or two) and estimate ph between them.
           If the chart does not appear, use the universal indicator scale, set referenceChartVisible=false and a low confidence.
           phLow and phHigh must reflect the real uncertainty of the color, not that of an instrument.
        3. METER_DISPLAY: transcribe exactly the pH number on the display. If the display shows the temperature,
           put it in temperatureC and temperatureVisible=true; otherwise temperatureC=0 and temperatureVisible=false.
        4. If the photo does not allow a reading (blurry, no strip, no display, not a pH test), readable=false and ph=0.
           Never invent a value.
        5. issues: problems of the photo that affect the reading. observations: one or two sentences in Spanish explaining what you compared.
        Write issues and observations in Spanish.""";

    private final ImageSanitizer sanitizer;
    private final AiProviderProxy aiProvider;
    private final PhotoReadingValidator validator;
    private final MeasurementService measurementService;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public PhotoReadingService(ImageSanitizer sanitizer, AiProviderProxy aiProvider, PhotoReadingValidator validator,
                               MeasurementService measurementService) {
        this.sanitizer = sanitizer;
        this.aiProvider = aiProvider;
        this.validator = validator;
        this.measurementService = measurementService;
    }

    public PhotoReadingResponse analyze(Long userId, byte[] image, PhotoKind hint) {
        byte[] clean = sanitizer.sanitize(image);
        if (!aiProvider.isAvailable() || aiProvider.chain().stream().noneMatch(p -> p.isAvailable() && p.supportsVision())) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "La lectura por foto no está disponible: no hay un proveedor de IA con visión configurado. Registra el valor manualmente.");
        }
        AiPrompt prompt = new AiPrompt(userId, "PHOTO_READING", SYSTEM_PROMPT,
            "Type indicated by the user: " + (hint == null ? "unknown" : hint.name()) + ". Analyze the photo.",
            PhotoReadingSchema.NAME, PhotoReadingSchema.SCHEMA, new AiImage("image/jpeg", clean), Set.of());
        PhotoReading first;
        try {
            first = read(prompt);
        } catch (AiUnavailableException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo analizar la foto: " + ex.getMessage());
        } catch (PhotoReadingValidator.InvalidPhotoReadingException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                "La IA devolvió una lectura inválida (" + ex.getMessage() + "). Intenta con otra foto o registra el valor manualmente.");
        }
        List<PhotoReadingResponse.Opinion> opinions = new ArrayList<>(List.of(opinion(first)));
        PhotoReading result = first;
        String agreement = "SINGLE";
        String note = null;
        if (first.readable() && first.kind() == PhotoKind.TEST_STRIP) {
            try {
                PhotoReading second = read(secondOpinionPrompt(prompt, first));
                opinions.add(opinion(second));
                if (second.readable() && second.kind() == PhotoKind.TEST_STRIP) {
                    boolean agree = Math.abs(first.ph() - second.ph()) <= AGREEMENT_TOLERANCE;
                    result = combine(first, second, agree);
                    agreement = agree ? "AGREE" : "DISAGREE";
                } else {
                    result = withCap(first, 0.5, "La segunda opinión no pudo leer la tira; confianza limitada a 0.50.");
                    agreement = "PARTIAL";
                }
            } catch (AiUnavailableException | PhotoReadingValidator.InvalidPhotoReadingException | BusinessException ex) {
                note = "Sin segunda opinión: " + ex.getMessage();
            }
        }
        String pendingId = null;
        if (result.readable()) {
            cleanup();
            pendingId = UUID.randomUUID().toString();
            pending.put(pendingId, new Pending(userId, result, Instant.now()));
        }
        return toResponse(pendingId, result, opinions, agreement, note);
    }

    public MeasurementResponse save(Long userId, String pendingId, SavePhotoReadingRequest request) {
        Pending entry = pending.get(pendingId);
        if (entry == null || !entry.userId().equals(userId) || entry.isExpired()) {
            throw BusinessException.notFound("La lectura ya no está disponible. Vuelve a analizar la foto.");
        }
        pending.remove(pendingId);
        PhotoReading reading = entry.reading();
        double value = request.ph() == null ? reading.ph() : Math.round(request.ph() * 100) / 100.0;
        boolean corrected = Math.abs(value - reading.ph()) > 0.001;
        String note = String.format(Locale.US, "Foto (%s) leída por IA: %.2f [%.2f-%.2f], confianza %.2f, %s.%s%s",
            reading.kind() == PhotoKind.TEST_STRIP ? "tira" : "pantalla", reading.ph(), reading.phLow(), reading.phHigh(),
            reading.confidence(), reading.provider(), corrected ? " Valor corregido por el usuario." : "",
            request.note() == null || request.note().isBlank() ? "" : " " + request.note().trim());
        if (note.length() > 300) {
            note = note.substring(0, 300);
        }
        return measurementService.toResponse(userId, measurementService.record(userId,
            new NewReading(value, reading.temperatureC(), Instant.now(), MeasurementSource.PHOTO, null, note)));
    }

    private PhotoReading read(AiPrompt prompt) {
        AiAnswer answer = aiProvider.complete(prompt);
        return validator.validate(answer.text(), answer.provider(), answer.model());
    }

    private AiPrompt secondOpinionPrompt(AiPrompt prompt, PhotoReading first) {
        Set<String> sameProvider = new LinkedHashSet<>();
        for (AiTextProvider provider : aiProvider.chain()) {
            if (provider.providerName().equals(first.provider())) {
                sameProvider.add(provider.modelName());
            }
        }
        boolean otherProviderAvailable = aiProvider.chain().stream().anyMatch(p -> p.isAvailable() && p.supportsVision()
            && !sameProvider.contains(p.modelName()));
        AiPrompt second = prompt.excluding(first.model());
        if (otherProviderAvailable) {
            for (String model : sameProvider) {
                second = second.excluding(model);
            }
        }
        return second;
    }

    private static PhotoReading combine(PhotoReading a, PhotoReading b, boolean agree) {
        double ph = Math.round((a.ph() + b.ph()) / 2 * 10) / 10.0;
        double low = Math.min(a.phLow(), b.phLow());
        double high = Math.max(a.phHigh(), b.phHigh());
        double confidence = Math.min(a.confidence(), b.confidence());
        List<String> adjustments = new ArrayList<>(a.adjustments());
        LinkedHashSet<PhotoIssue> issues = new LinkedHashSet<>(a.issues());
        issues.addAll(b.issues());
        if (agree) {
            adjustments.add(String.format(Locale.US, "Segunda opinión coincidente: %s leyó %.1f y %s leyó %.1f.",
                a.model(), a.ph(), b.model(), b.ph()));
        } else {
            low = Math.max(0, Math.min(low, Math.min(a.ph(), b.ph()) - 0.25));
            high = Math.min(14, Math.max(high, Math.max(a.ph(), b.ph()) + 0.25));
            confidence = Math.min(confidence, 0.35);
            issues.add(PhotoIssue.AMBIGUOUS_COLOR);
            adjustments.add(String.format(Locale.US, "Los modelos no coinciden: %s leyó %.1f y %s leyó %.1f. "
                + "Compara tú mismo la tira con la carta de colores.", a.model(), a.ph(), b.model(), b.ph()));
        }
        return new PhotoReading(true, PhotoKind.TEST_STRIP, ph, Math.round(low * 100) / 100.0, Math.round(high * 100) / 100.0,
            confidence, a.referenceChartVisible() || b.referenceChartVisible(), null, List.copyOf(issues),
            a.observations(), a.provider() + " + " + b.provider(), a.model() + " + " + b.model(), adjustments);
    }

    private static PhotoReading withCap(PhotoReading reading, double cap, String adjustment) {
        List<String> adjustments = new ArrayList<>(reading.adjustments());
        adjustments.add(adjustment);
        return new PhotoReading(reading.readable(), reading.kind(), reading.ph(), reading.phLow(), reading.phHigh(),
            Math.min(reading.confidence(), cap), reading.referenceChartVisible(), reading.temperatureC(), reading.issues(),
            reading.observations(), reading.provider(), reading.model(), adjustments);
    }

    private static PhotoReadingResponse.Opinion opinion(PhotoReading reading) {
        return new PhotoReadingResponse.Opinion(reading.provider(), reading.model(), reading.readable(), reading.ph(),
            reading.kind().name());
    }

    private static PhotoReadingResponse toResponse(String pendingId, PhotoReading r, List<PhotoReadingResponse.Opinion> opinions,
                                                   String agreement, String note) {
        String agreementLabel = switch (agreement) {
            case "AGREE" -> "Dos modelos coinciden";
            case "DISAGREE" -> "Los modelos no coinciden";
            case "PARTIAL" -> "Segunda opinión no concluyente";
            default -> "Una sola lectura";
        };
        return new PhotoReadingResponse(pendingId, r.readable(), r.kind().name(), r.kind().label(), r.ph(), r.phLow(),
            r.phHigh(), r.confidence(), r.referenceChartVisible(), r.temperatureC(),
            r.issues().stream().map(i -> new PhotoReadingResponse.Issue(i.name(), i.label())).toList(), r.observations(),
            opinions, agreement, agreementLabel, r.adjustments(), note, DISCLAIMER);
    }

    /** Photo readings nobody confirmed are removed every 10 minutes. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void forgetUnconfirmedReadings() {
        cleanup();
    }

    private void cleanup() {
        pending.values().removeIf(Pending::isExpired);
    }

    private record Pending(Long userId, PhotoReading reading, Instant createdAt) {
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(PENDING_TTL));
        }
    }
}
