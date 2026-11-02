package com.aquasense.device.api;

import com.aquasense.device.api.DeviceDtos.DeviceRequest;
import com.aquasense.device.api.DeviceDtos.DeviceResponse;
import com.aquasense.device.service.DeviceService;
import com.aquasense.identity.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Dispositivos")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> register(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody DeviceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceResponse.from(
                service.register(user.id(), r.name(), r.manufacturer(), r.model(), r.externalId(),
                        r.connectionType())));
    }

    @GetMapping
    public List<DeviceResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id()).stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return DeviceResponse.from(service.get(id, user.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.unlink(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
