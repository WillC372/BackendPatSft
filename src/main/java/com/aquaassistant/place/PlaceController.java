package com.aquaassistant.place;

import com.aquaassistant.place.dto.PlaceDtos.JoinRequest;
import com.aquaassistant.place.dto.PlaceDtos.MemberResponse;
import com.aquaassistant.place.dto.PlaceDtos.PlaceRequest;
import com.aquaassistant.place.dto.PlaceDtos.PlaceResponse;
import com.aquaassistant.place.dto.PlaceDtos.ShareRequest;
import com.aquaassistant.security.CurrentUser;
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
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService service;

    public PlaceController(PlaceService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlaceResponse> list() {
        return service.list(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public PlaceResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceResponse create(@Valid @RequestBody PlaceRequest request) {
        return service.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public PlaceResponse update(@PathVariable Long id, @Valid @RequestBody PlaceRequest request) {
        return service.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/activate")
    public PlaceResponse activate(@PathVariable Long id) {
        return service.activate(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/share")
    public PlaceResponse share(@PathVariable Long id, @Valid @RequestBody ShareRequest request) {
        return service.share(CurrentUser.id(), id, PlaceRole.valueOf(request.role()));
    }

    @DeleteMapping("/{id}/share")
    public PlaceResponse stopSharing(@PathVariable Long id) {
        return service.stopSharing(CurrentUser.id(), id);
    }

    @PostMapping("/join")
    public PlaceResponse join(@Valid @RequestBody JoinRequest request) {
        return service.join(CurrentUser.id(), request.code());
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long id) {
        service.leave(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> members(@PathVariable Long id) {
        return service.members(CurrentUser.id(), id);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        service.removeMember(CurrentUser.id(), id, userId);
    }
}
