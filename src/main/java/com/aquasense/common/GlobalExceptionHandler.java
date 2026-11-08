package com.aquasense.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejo centralizado de errores: traduce excepciones a respuestas JSON en español
 * sin exponer trazas internas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        HttpStatus status = ex.getStatus();
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(com.aquasense.device.hardware.DeviceException.class)
    public ResponseEntity<ApiError> handleDevice(com.aquasense.device.hardware.DeviceException ex) {
        return ResponseEntity.status(503).body(ApiError.of(503, "Service Unavailable", ex.getMessage()));
    }

    @ExceptionHandler(com.aquasense.intelligence.ai.AiUnavailableException.class)
    public ResponseEntity<ApiError> handleAi(com.aquasense.intelligence.ai.AiUnavailableException ex) {
        LOG.warn("AI provider unavailable: {}", ex.getMessage());
        return ResponseEntity.status(503).body(ApiError.of(503, "Service Unavailable", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ApiError body = new ApiError(Instant.now(), 400, "Bad Request", "Revisa los campos marcados.", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", "La solicitud tiene un formato inválido."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiError.of(403, "Forbidden", "No tienes permiso para esta operación."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleUnauthorized(AuthenticationException ex) {
        return ResponseEntity.status(401).body(ApiError.of(401, "Unauthorized", "Credenciales inválidas o sesión expirada."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return ResponseEntity.status(500).body(ApiError.of(500, "Internal Server Error", "Ocurrió un error inesperado. Intenta de nuevo."));
    }
}
