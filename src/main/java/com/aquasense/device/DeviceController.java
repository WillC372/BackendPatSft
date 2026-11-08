package com.aquasense.device;

import com.aquasense.device.dto.CalibrateRequest;
import com.aquasense.device.dto.ConnectRequest;
import com.aquasense.device.dto.DetectedDeviceResponse;
import com.aquasense.device.dto.DeviceStatusResponse;
import com.aquasense.device.dto.ReadingPreviewResponse;
import com.aquasense.device.dto.RegisteredDeviceResponse;
import com.aquasense.device.dto.SaveReadingRequest;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.security.CurrentUser;
import com.aquasense.user.dto.MessageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService service;
    private final LiveFeed liveFeed;

    public DeviceController(DeviceService service, LiveFeed liveFeed) {
        this.service = service;
        this.liveFeed = liveFeed;
    }

    @GetMapping("/scan")
    public List<DetectedDeviceResponse> scan() {
        return service.scan(CurrentUser.id());
    }

    @GetMapping
    public List<RegisteredDeviceResponse> registered() {
        return service.registered(CurrentUser.id());
    }

    @GetMapping("/status")
    public DeviceStatusResponse status() {
        return service.status(CurrentUser.id());
    }

    @PostMapping("/connect")
    public DeviceStatusResponse connect(@Valid @RequestBody ConnectRequest request) {
        return service.connect(CurrentUser.id(), request);
    }

    @PostMapping("/disconnect")
    public DeviceStatusResponse disconnect() {
        return service.disconnect(CurrentUser.id());
    }

    @PostMapping("/measure")
    public ReadingPreviewResponse measure() {
        return service.measure(CurrentUser.id());
    }

    @PostMapping("/readings/{pendingId}/save")
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse save(@PathVariable String pendingId, @Valid @RequestBody(required = false) SaveReadingRequest request) {
        return service.savePending(CurrentUser.id(), pendingId, request == null ? null : request.note());
    }

    @PostMapping("/calibrate")
    public MessageResponse calibrate(@Valid @RequestBody CalibrateRequest request) {
        return new MessageResponse(service.calibrate(CurrentUser.id(), request));
    }

    @PostMapping("/live/start")
    public DeviceStatusResponse startLive() {
        return service.startLive(CurrentUser.id());
    }

    @PostMapping("/live/stop")
    public DeviceStatusResponse stopLive() {
        return service.stopLive(CurrentUser.id());
    }

    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() {
        return liveFeed.subscribe(CurrentUser.id());
    }
}
