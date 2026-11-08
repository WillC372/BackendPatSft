package com.aquasense.intelligence.ai;

import com.aquasense.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consumo y costos de IA: cada usuario ve el suyo; la administración ve el global (ruta protegida por rol ADMIN).
 */
@RestController
public class AiUsageController {

    private final AiUsageService service;

    public AiUsageController(AiUsageService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/intelligence/ai-usage")
    public AiUsageReport mine(@RequestParam(defaultValue = "30") int days) {
        return service.forUser(CurrentUser.id(), days);
    }

    @GetMapping("/api/v1/admin/ai-usage")
    public AiUsageReport global(@RequestParam(defaultValue = "30") int days) {
        return service.global(days);
    }
}
