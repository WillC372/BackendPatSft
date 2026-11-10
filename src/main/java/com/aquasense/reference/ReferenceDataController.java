package com.aquasense.reference;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Official public pH data (USGS), kept apart from the users' own measurements. */
@RestController
@RequestMapping("/api/v1/reference")
public class ReferenceDataController {

    private final ReferenceDataService service;

    public ReferenceDataController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ReferenceDataService.Summary summary() {
        return service.summary();
    }

    @GetMapping("/stations/{id}/readings")
    public ReferenceDataService.Series readings(@PathVariable String id, @RequestParam(defaultValue = "7") int days) {
        return service.series(id, days);
    }
}
