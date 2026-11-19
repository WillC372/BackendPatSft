package com.aquaassistant.security;

import com.aquaassistant.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The key the backend and the AI service share for their calls to each other (INTERNAL_API_KEY). Without a key
 * configured the internal endpoints answer as if they did not exist.
 */
@Component
public class InternalKey {

    public static final String HEADER = "X-Internal-Key";
    private final byte[] key;

    public InternalKey(@Value("${aquasense.internal-key:}") String key) {
        this.key = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    }

    public void check(String given) {
        if (key.length < 16 || given == null || !MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "No encontrado.");
        }
    }
}
