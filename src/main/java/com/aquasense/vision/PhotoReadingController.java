package com.aquasense.vision;

import com.aquasense.common.BusinessException;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.security.CurrentUser;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vision")
public class PhotoReadingController {

    private final PhotoReadingService service;

    public PhotoReadingController(PhotoReadingService service) {
        this.service = service;
    }

    @PostMapping(value = "/ph-reading", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoReadingResponse analyze(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "kind", required = false) PhotoKind kind) {
        try {
            return service.analyze(CurrentUser.id(), file.getBytes(), kind);
        } catch (IOException ex) {
            throw BusinessException.badRequest("No se pudo leer la foto enviada.");
        }
    }

    @PostMapping("/ph-reading/{pendingId}/save")
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse save(@PathVariable String pendingId, @Valid @RequestBody SavePhotoReadingRequest request) {
        return service.save(CurrentUser.id(), pendingId, request);
    }
}
