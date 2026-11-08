package com.aquasense.alert;

import com.aquasense.security.CurrentUser;
import com.aquasense.user.dto.MessageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public List<AlertResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                    @RequestParam(defaultValue = "50") int limit) {
        return service.list(CurrentUser.id(), unreadOnly, limit);
    }

    @PatchMapping("/{id}/read")
    public AlertResponse markRead(@PathVariable Long id) {
        return service.markRead(CurrentUser.id(), id);
    }

    @PostMapping("/read-all")
    public MessageResponse markAllRead() {
        int updated = service.markAllRead(CurrentUser.id());
        return new MessageResponse(updated + " alertas marcadas como leidas.");
    }
}
