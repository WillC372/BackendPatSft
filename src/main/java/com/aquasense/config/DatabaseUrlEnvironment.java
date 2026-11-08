package com.aquasense.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts the connection string that hosted PostgreSQL providers hand out (Neon, Render, Heroku):
 * {@code postgresql://user:password@host[:port]/database?sslmode=require}. When {@code DATABASE_URL} is set and
 * {@code DB_URL} is not, it is turned into the JDBC URL, user and password Spring expects, so the whole database
 * configuration is a single variable. Explicit {@code DB_URL}/{@code DB_USER}/{@code DB_PASSWORD} always win.
 */
public class DatabaseUrlEnvironment implements EnvironmentPostProcessor {

    static final String SOURCE = "databaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank() || environment.containsProperty("DB_URL")) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, toSpring(raw.trim())));
    }

    /** Converts a postgres(ql):// URL into Spring datasource properties. */
    static Map<String, Object> toSpring(String raw) {
        URI uri = URI.create(raw);
        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgres:// or postgresql://");
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
        if (uri.getRawQuery() != null) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbc.toString());
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            props.put("spring.datasource.username", decode(colon < 0 ? userInfo : userInfo.substring(0, colon)));
            if (colon >= 0) {
                props.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return props;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
