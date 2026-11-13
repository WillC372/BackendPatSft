package com.aquasense.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Address of the person making a request, for the attempt limits. On Render every request arrives through
 * Cloudflare, so the address the server sees changes from request to request (it is an intermediate hop) and a limit
 * by that address never fills up. Cloudflare puts the real address in CF-Connecting-IP and replaces any value a client
 * sends in it, so that header is used when configured ({@code CLIENT_IP_HEADER}). Without it, the connection address.
 */
@Component
public class ClientAddress {

    private static final Pattern IP = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final String header;

    public ClientAddress(@Value("${aquasense.security.client-ip-header:}") String header) {
        this.header = header == null ? "" : header.trim();
    }

    public String of(HttpServletRequest request) {
        if (!header.isEmpty()) {
            String value = request.getHeader(header);
            if (value != null) {
                String first = value.split(",")[0].trim();
                if (IP.matcher(first).matches()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
