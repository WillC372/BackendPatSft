package com.aquaassistant.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends notifications to the phones and computers where the person accepted them (Web Push). The key pair that
 * identifies this server is created the first time and saved in the database, so no setting is needed.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String PUBLIC = "webpush.public";
    private static final String PRIVATE = "webpush.private";

    private final AppKeyRepository keys;
    private final PushSubscriptionRepository subscriptions;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final String subject;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private volatile KeyPair keyPair;

    public PushService(AppKeyRepository keys, PushSubscriptionRepository subscriptions, TransactionTemplate transactions,
                       ObjectMapper mapper, @Value("${aquasense.push.subject:https://aquaassistantai.vercel.app}") String subject) {
        this.keys = keys;
        this.subscriptions = subscriptions;
        this.transactions = transactions;
        this.mapper = mapper;
        this.subject = subject;
    }

    /** Public key the browser needs to subscribe (base64url, uncompressed P-256 point). */
    public String publicKey() {
        return WebPushCrypto.b64url(WebPushCrypto.raw((ECPublicKey) keys().getPublic()));
    }

    /** Sends to every device of the person; devices the push service no longer knows are removed. Returns how many got it. */
    public int send(Long userId, String title, String body, String url) {
        List<PushSubscription> targets = subscriptions.findByUserId(userId);
        if (targets.isEmpty()) {
            return 0;
        }
        byte[] payload;
        try {
            payload = mapper.writeValueAsString(Map.of("title", title, "body", body, "url", url == null ? "/" : url))
                .getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return 0;
        }
        int delivered = 0;
        for (PushSubscription target : targets) {
            int status = deliver(target, payload);
            if (status >= 200 && status < 300) {
                delivered++;
                transactions.executeWithoutResult(t -> subscriptions.findById(target.getId()).ifPresent(PushSubscription::sent));
            } else if (status == 404 || status == 410) {
                transactions.executeWithoutResult(t -> subscriptions.deleteById(target.getId()));
            }
        }
        return delivered;
    }

    private int deliver(PushSubscription target, byte[] payload) {
        try {
            URI endpoint = URI.create(target.getEndpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getHost() + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
            KeyPair pair = keys();
            String vapid = WebPushCrypto.vapidHeader(audience, subject, pair.getPrivate(), (ECPublicKey) pair.getPublic(),
                Instant.now().plus(Duration.ofHours(12)).getEpochSecond());
            byte[] body = WebPushCrypto.encrypt(payload, target.getP256dh(), target.getAuth());
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                .header("Authorization", vapid).header("TTL", "86400").header("Urgency", "normal")
                .header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 400 && status != 404 && status != 410) {
                log.warn("Push service answered {} for subscription {}", status, target.getId());
            }
            return status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            log.warn("Push to subscription {} failed: {}", target.getId(), e.getMessage());
            return 0;
        }
    }

    /** The server's key pair, created and saved the first time. */
    KeyPair keys() {
        KeyPair current = keyPair;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (keyPair == null) {
                keyPair = transactions.execute(t -> {
                    try {
                        var pub = keys.findById(PUBLIC);
                        var priv = keys.findById(PRIVATE);
                        if (pub.isPresent() && priv.isPresent()) {
                            return new KeyPair(WebPushCrypto.decodePublic(pub.get().getValue()), WebPushCrypto.decodePrivate(priv.get().getValue()));
                        }
                        KeyPair created = WebPushCrypto.newKeyPair();
                        keys.save(new AppKey(PUBLIC, Base64.getEncoder().encodeToString(created.getPublic().getEncoded())));
                        keys.save(new AppKey(PRIVATE, Base64.getEncoder().encodeToString(created.getPrivate().getEncoded())));
                        return created;
                    } catch (java.security.GeneralSecurityException e) {
                        throw new IllegalStateException("Web Push keys could not be created", e);
                    }
                });
            }
            return keyPair;
        }
    }

    PrivateKey privateKeyForTests() {
        return keys().getPrivate();
    }
}
