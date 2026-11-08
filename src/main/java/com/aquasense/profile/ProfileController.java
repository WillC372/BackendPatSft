package com.aquasense.profile;

import com.aquasense.profile.dto.ProfileRequest;
import com.aquasense.profile.dto.ProfileResponse;
import com.aquasense.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProfileResponse> list() {
        Long userId = CurrentUser.id();
        Long activeId = service.activeProfile(userId).getId();
        return service.listFor(userId).stream().map(p -> ProfileResponse.from(p, activeId)).toList();
    }

    @GetMapping("/active")
    public ProfileResponse active() {
        WaterProfile profile = service.activeProfile(CurrentUser.id());
        return ProfileResponse.from(profile, profile.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse create(@Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(service.create(CurrentUser.id(), request), null);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse duplicate(@PathVariable Long id) {
        return ProfileResponse.from(service.duplicate(CurrentUser.id(), id), null);
    }

    @PutMapping("/{id}")
    public ProfileResponse update(@PathVariable Long id, @Valid @RequestBody ProfileRequest request) {
        Long userId = CurrentUser.id();
        return ProfileResponse.from(service.update(userId, id, request), service.activeProfile(userId).getId());
    }

    @PostMapping("/{id}/activate")
    public ProfileResponse activate(@PathVariable Long id) {
        WaterProfile profile = service.activate(CurrentUser.id(), id);
        return ProfileResponse.from(profile, profile.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }
}
