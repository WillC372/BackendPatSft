package com.aquasense.waterprofile.api;

import com.aquasense.identity.security.AuthenticatedUser;
import com.aquasense.waterprofile.api.ProfileDtos.ClassificationResponse;
import com.aquasense.waterprofile.api.ProfileDtos.DuplicateRequest;
import com.aquasense.waterprofile.api.ProfileDtos.ProfileRequest;
import com.aquasense.waterprofile.api.ProfileDtos.ProfileResponse;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.service.WaterProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Perfiles de agua")
@SecurityRequirement(name = "bearerAuth")
public class WaterProfileController {

    private final WaterProfileService service;

    public WaterProfileController(WaterProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProfileResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id()).stream().map(ProfileResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ProfileResponse.from(service.get(id, user.id()));
    }

    @PostMapping
    public ResponseEntity<ProfileResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(service.create(user.id(), request)));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ProfileResponse> duplicate(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody DuplicateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(service.duplicate(id, user.id(), request.name())));
    }

    @PutMapping("/{id}")
    public ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                  @Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(service.update(id, user.id(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(id, user.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/classify")
    public ClassificationResponse classify(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                           @RequestParam BigDecimal ph) {
        WaterProfile profile = service.get(id, user.id());
        return ClassificationResponse.from(profile.classify(ph), profile);
    }
}
