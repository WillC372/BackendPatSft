package com.poolguard.auth;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SINGLETON PATTERN
 *
 * Centralizes the administration of the active user sessions. There is exactly
 * one SessionManager instance in the whole application, shared by the API
 * layer, the monitoring service and the command invoker.
 */
public final class SessionManager {

    private static SessionManager instance;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private SessionManager() {
    }

    /** Returns the unique instance of the session manager (thread-safe). */
    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /** Creates a new session for the given user and returns its token. */
    public String createSession(User user) {
        String token = generateToken();
        sessions.put(token, new Session(user, System.currentTimeMillis()));
        return token;
    }

    /** Returns the session bound to a token, or null if invalid/expired. */
    public Session getSession(String token) {
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        if (session != null && System.currentTimeMillis() - session.createdAt > 8 * 3600_000L) {
            sessions.remove(token); // expired (8 h)
            return null;
        }
        return session;
    }

    /** Invalidates a session (logout). */
    public void invalidate(String token) {
        sessions.remove(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** An active session: authenticated user + creation time. */
    public static final class Session {
        public final User user;
        public final long createdAt;

        Session(User user, long createdAt) {
            this.user = user;
            this.createdAt = createdAt;
        }
    }
}
