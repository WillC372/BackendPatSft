package com.aquaassistant.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    @Test
    void aBrowserCanDecryptWhatTheServerSends() throws Exception {
        // The browser's keys (what PushManager.subscribe gives as p256dh and auth).
        KeyPair browser = WebPushCrypto.newKeyPair();
        byte[] authSecret = new byte[16];
        new java.security.SecureRandom().nextBytes(authSecret);
        String p256dh = WebPushCrypto.b64url(WebPushCrypto.raw((ECPublicKey) browser.getPublic()));
        String auth = WebPushCrypto.b64url(authSecret);
        byte[] message = "{\"title\":\"pH crítico\",\"body\":\"pH 8,90\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(message, p256dh, auth);

        // Decrypt as the browser does (RFC 8291): header = salt(16) | rs(4) | idlen(1) | sender key(65) | ciphertext.
        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        assertThat(in.getInt()).isEqualTo(4096);
        byte[] senderRaw = new byte[in.get()];
        in.get(senderRaw);
        byte[] ciphertext = Arrays.copyOfRange(body, in.position(), body.length);
        ECPublicKey sender = WebPushCrypto.fromRaw(senderRaw, ((ECPublicKey) browser.getPublic()).getParams());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(sender, true);
        byte[][] keyNonce = WebPushCrypto.deriveKeyAndNonce(agreement.generateSecret(), authSecret,
            WebPushCrypto.raw((ECPublicKey) browser.getPublic()), senderRaw, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyNonce[0], "AES"), new GCMParameterSpec(128, keyNonce[1]));
        byte[] plain = cipher.doFinal(ciphertext);

        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        assertThat(new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8)).isEqualTo(new String(message, StandardCharsets.UTF_8));
    }

    @Test
    void theVapidTokenIsSignedWithTheServerKey() throws Exception {
        KeyPair server = WebPushCrypto.newKeyPair();
        String header = WebPushCrypto.vapidHeader("https://fcm.googleapis.com", "https://example.com", server.getPrivate(),
            (ECPublicKey) server.getPublic(), 2_000_000_000L);
        assertThat(header).startsWith("vapid t=").contains(", k=" + WebPushCrypto.b64url(WebPushCrypto.raw((ECPublicKey) server.getPublic())));
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"exp\":2000000000");
        byte[] raw = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(raw).hasSize(64);
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(server.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(raw)).isTrue();
    }
}
