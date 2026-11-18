package com.aquaassistant.notify;

import com.aquaassistant.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Devices where the person receives notifications (phone or computer) and a test notification. */
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    public record SubscribeRequest(
        @NotBlank @Size(max = 600) @Pattern(regexp = "https://.+", message = "Dirección de notificaciones no válida.") String endpoint,
        @NotNull @Valid Keys keys,
        @Size(max = 120) String label) {
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = 600) String endpoint) {
    }

    public record DeviceResponse(Long id, String label, Instant createdAt, Instant lastSentAt) {
    }

    private final PushService push;
    private final PushSubscriptionRepository subscriptions;
    private final Notifier notifier;

    public PushController(PushService push, PushSubscriptionRepository subscriptions, Notifier notifier) {
        this.push = push;
        this.subscriptions = subscriptions;
        this.notifier = notifier;
    }

    @GetMapping("/key")
    public Map<String, String> key() {
        return Map.of("publicKey", push.publicKey());
    }

    @GetMapping("/devices")
    public List<DeviceResponse> devices() {
        return subscriptions.findByUserId(CurrentUser.id()).stream()
            .map(s -> new DeviceResponse(s.getId(), s.getDeviceLabel(), s.getCreatedAt(), s.getLastSentAt())).toList();
    }

    @PostMapping("/subscriptions")
    @Transactional
    public DeviceResponse subscribe(@Valid @RequestBody SubscribeRequest request) {
        Long userId = CurrentUser.id();
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
            .map(existing -> {
                existing.renew(userId, request.keys().p256dh(), request.keys().auth(), request.label());
                return existing;
            })
            .orElseGet(() -> subscriptions.save(new PushSubscription(userId, request.endpoint(), request.keys().p256dh(),
                request.keys().auth(), request.label())));
        return new DeviceResponse(subscription.getId(), subscription.getDeviceLabel(), subscription.getCreatedAt(), subscription.getLastSentAt());
    }

    @DeleteMapping("/subscriptions")
    @Transactional
    public Map<String, Boolean> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        Long userId = CurrentUser.id();
        boolean removed = subscriptions.findByEndpoint(request.endpoint()).filter(s -> s.getUserId().equals(userId))
            .map(s -> { subscriptions.delete(s); return true; }).orElse(false);
        return Map.of("removed", removed);
    }

    @PostMapping("/test")
    public Map<String, Integer> test() {
        return Map.of("delivered", notifier.testPush(CurrentUser.id()));
    }
}
