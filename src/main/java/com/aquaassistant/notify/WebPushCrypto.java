package com.aquaassistant.notify;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push with the JDK only: message encryption "aes128gcm" (RFC 8291 / RFC 8188) and the VAPID signature that
 * identifies this server to the push services (RFC 8292). Keys are P-256 (secp256r1).
 */
public final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_IN = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;

    private WebPushCrypto() {
    }

    public static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    /** Uncompressed point (0x04 || X || Y), 65 bytes: the format browsers use for keys. */
    public static byte[] raw(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static ECPublicKey fromRaw(byte[] raw, ECParameterSpec params) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) {
            throw new GeneralSecurityException("The browser key is not an uncompressed P-256 point.");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, params));
    }

    /** Encrypts the payload for one subscription (keys p256dh and auth, base64url as the browser gives them). */
    public static byte[] encrypt(byte[] payload, String p256dhB64, String authB64) throws GeneralSecurityException {
        byte[] uaPublicRaw = B64URL_IN.decode(p256dhB64);
        byte[] authSecret = B64URL_IN.decode(authB64);
        KeyPair ephemeral = newKeyPair();
        ECPublicKey asPublic = (ECPublicKey) ephemeral.getPublic();
        ECPublicKey uaPublic = fromRaw(uaPublicRaw, asPublic.getParams());
        byte[] asPublicRaw = raw(asPublic);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(uaPublic, true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[][] keyAndNonce = deriveKeyAndNonce(ecdhSecret, authSecret, uaPublicRaw, asPublicRaw, salt);

        byte[] plaintext = Arrays.copyOf(payload, payload.length + 1);
        plaintext[payload.length] = 2; // last (and only) record
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyAndNonce[0], "AES"), new GCMParameterSpec(128, keyAndNonce[1]));
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + asPublicRaw.length + ciphertext.length);
        body.put(salt).putInt(RECORD_SIZE).put((byte) asPublicRaw.length).put(asPublicRaw).put(ciphertext);
        return body.array();
    }

    /** RFC 8291 section 3.4: content encryption key (16 bytes) and nonce (12 bytes). */
    static byte[][] deriveKeyAndNonce(byte[] ecdhSecret, byte[] authSecret, byte[] uaPublicRaw, byte[] asPublicRaw, byte[] salt)
        throws GeneralSecurityException {
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublicRaw, asPublicRaw, new byte[] {1});
        byte[] ikm = hmac(prkKey, keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);
        return new byte[][] {cek, nonce};
    }

    /** VAPID header value: "vapid t=<signed JWT>, k=<public key>". */
    public static String vapidHeader(String audience, String subject, PrivateKey privateKey, ECPublicKey publicKey, long expiresEpochSeconds)
        throws GeneralSecurityException {
        String header = B64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64URL.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresEpochSeconds + ",\"sub\":\"" + subject + "\"}")
            .getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        String jwt = header + "." + claims + "." + B64URL.encodeToString(derToRaw(signer.sign()));
        return "vapid t=" + jwt + ", k=" + B64URL.encodeToString(raw(publicKey));
    }

    /** ECDSA signatures from the JDK are DER; JWT (ES256) wants r || s, 32 bytes each. */
    static byte[] derToRaw(byte[] der) {
        int offset = 3;
        int rLength = der[offset++];
        byte[] r = Arrays.copyOfRange(der, offset, offset + rLength);
        offset += rLength + 1;
        int sLength = der[offset++];
        byte[] s = Arrays.copyOfRange(der, offset, offset + sLength);
        byte[] out = new byte[64];
        copy32(new BigInteger(1, r), out, 0);
        copy32(new BigInteger(1, s), out, 32);
        return out;
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static void copy32(BigInteger value, byte[] out, int at) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, out, at + 32 - length, length);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    public static String b64url(byte[] bytes) {
        return B64URL.encodeToString(bytes);
    }

    public static PublicKey decodePublic(String x509B64) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(x509B64)));
    }

    public static PrivateKey decodePrivate(String pkcs8B64) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(pkcs8B64)));
    }
}
