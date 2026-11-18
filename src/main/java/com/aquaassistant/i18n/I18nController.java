package com.aquaassistant.i18n;

import com.aquaassistant.common.BusinessException;
import com.aquaassistant.security.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Languages of the interface. Open before signing in, so the login screen can be shown in the chosen language. */
@RestController
@RequestMapping("/api/v1/i18n")
public class I18nController {

    private final TranslationService translations;
    private final ClientAddress clientAddress;

    public I18nController(TranslationService translations, ClientAddress clientAddress) {
        this.translations = translations;
        this.clientAddress = clientAddress;
    }

    public record LanguageResponse(String code, String name, String englishName, boolean rtl, boolean pdf) {
    }

    public record TranslateRequest(
        @NotBlank String lang,
        @NotNull @Size(max = 80, message = "Máximo 80 textos por solicitud.") List<@Size(max = TranslationService.MAX_TEXT) String> texts) {
    }

    public record TranslateResponse(String lang, List<String> translations) {
    }

    @GetMapping("/languages")
    public ResponseEntity<List<LanguageResponse>> languages() {
        List<LanguageResponse> list = Languages.codes().stream()
            .map(code -> new LanguageResponse(code, Languages.nativeName(code), Languages.englishName(code), Languages.rightToLeft(code), Languages.drawableInPdf(code)))
            .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic()).body(list);
    }

    /** Everything already translated into a language, to load it in one request. */
    @GetMapping("/dictionary/{lang}")
    public ResponseEntity<Map<String, String>> dictionary(@PathVariable String lang) {
        String code = supported(lang);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic())
            .body(Languages.isOriginal(code) ? Map.of() : translations.dictionary(code));
    }

    @PostMapping("/translate")
    public TranslateResponse translate(@Valid @RequestBody TranslateRequest request, HttpServletRequest http) {
        String code = supported(request.lang());
        return new TranslateResponse(code, translations.translate(code, request.texts(), clientAddress.of(http)));
    }

    private static String supported(String lang) {
        String code = Languages.normalize(lang);
        if (code == null) {
            throw BusinessException.badRequest("Idioma no disponible.");
        }
        return code;
    }
}
