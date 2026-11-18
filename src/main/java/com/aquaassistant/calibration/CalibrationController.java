package com.aquaassistant.calibration;

import com.aquaassistant.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** History of the calibrations of the person's meters (like the GLP record of laboratory meters). */
@RestController
@RequestMapping("/api/v1/calibrations")
public class CalibrationController {

    public record CalibrationRequest(
        @NotBlank @Size(max = 80) String deviceName,
        @Size(max = 30) String protocol,
        @NotNull @Pattern(regexp = "LOW|MID|HIGH|CLEAR", message = "Punto de calibración no válido.") String point,
        @DecimalMin("0.0") @DecimalMax("14.0") Double bufferValue,
        @DecimalMin("0.0") @DecimalMax("14.0") Double reading,
        @Size(max = 200) String response,
        boolean success) {
    }

    public record CalibrationResponse(Long id, String deviceName, String protocol, String point, String pointLabel,
                                      Double bufferValue, Double reading, Double deviation, String response,
                                      boolean success, Instant createdAt) {

        static CalibrationResponse from(Calibration c) {
            Double deviation = c.getReading() == null || c.getBufferValue() == null ? null
                : Math.round((c.getReading() - c.getBufferValue()) * 100.0) / 100.0;
            String label = switch (c.getPoint()) {
                case "LOW" -> "Punto bajo (ácido)";
                case "HIGH" -> "Punto alto (alcalino)";
                case "CLEAR" -> "Calibración borrada";
                default -> "Punto medio (neutro)";
            };
            return new CalibrationResponse(c.getId(), c.getDeviceName(), c.getProtocol(), c.getPoint(), label,
                c.getBufferValue(), c.getReading(), deviation, c.getResponse(), c.isSuccess(), c.getCreatedAt());
        }
    }

    private final CalibrationRepository repository;
    private final CalibrationLog log;

    public CalibrationController(CalibrationRepository repository, CalibrationLog log) {
        this.repository = repository;
        this.log = log;
    }

    @GetMapping
    public List<CalibrationResponse> list(@RequestParam(defaultValue = "30") int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id(), PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
            .stream().map(CalibrationResponse::from).toList();
    }

    /** Calibrations done in the browser (USB meter): the page sends each step once the meter answered. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalibrationResponse record(@Valid @RequestBody CalibrationRequest r) {
        return CalibrationResponse.from(log.record(CurrentUser.id(), r.deviceName(), r.protocol(), r.point(), r.bufferValue(),
            r.reading(), r.response(), r.success()));
    }
}
