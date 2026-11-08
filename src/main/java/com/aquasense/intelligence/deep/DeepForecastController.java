package com.aquasense.intelligence.deep;

import com.aquasense.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeepForecastController {

    private final DeepForecastService service;

    public DeepForecastController(DeepForecastService service) {
        this.service = service;
    }

    /** Predicción con la red LSTM. {@code includeSimulated} solo sirve para pruebas: el simulador no es agua real. */
    @GetMapping("/api/v1/intelligence/deep/forecast")
    public DeepForecastResponse forecast(@RequestParam(defaultValue = "false") boolean includeSimulated) {
        return service.forecast(CurrentUser.id(), includeSimulated);
    }
}
