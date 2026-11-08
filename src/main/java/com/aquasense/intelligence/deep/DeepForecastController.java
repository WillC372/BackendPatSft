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

    /** Forecast with the LSTM network. {@code includeSimulated} is only for tests: the simulator is not real water. */
    @GetMapping("/api/v1/intelligence/deep/forecast")
    public DeepForecastResponse forecast(@RequestParam(defaultValue = "false") boolean includeSimulated) {
        return service.forecast(CurrentUser.id(), includeSimulated);
    }
}
