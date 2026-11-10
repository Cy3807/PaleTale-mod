package cn.paletale.companion;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Offline release signature for self-updates. The website only <em>serves</em> the signature; the private key stays on the publisher's machine
 * (see scripts/mod-sign.mjs), so a compromised website or CDN cannot make the mod accept a jar it did not sign.
 * Signed text: {@code "paletale-jar-v1\n" + file name + "\n" + SHA-256 of the jar (lower-case hex)}.
 */
final class UpdateTrust {
    /**
     * Release public keys (X.509 SubjectPublicKeyInfo, base64). To rotate: publish one build, signed with the old key, that lists both keys; later builds may drop the old one.
     */
    static final List<String> RELEASE_KEYS = List.of("MCowBQYDK2VwAyEA+eo6QI04UxwuB3iKMf7875Zn+prEr4eXkQ2V/2prRbw=");
    private UpdateTrust() {}

    static byte[] message(String file, String sha256Hex) {
        return ("paletale-jar-v1\n" + file + "\n" + sha256Hex).getBytes(StandardCharsets.UTF_8);
    }

    static boolean verify(String file, String sha256Hex, String signatureBase64) { return verify(RELEASE_KEYS, file, sha256Hex, signatureBase64); }

    /** True only when one of {@code keys} signed exactly this file name and digest. Any malformed input, or a Java runtime without Ed25519, is a refusal. */
    static boolean verify(List<String> keys, String file, String sha256Hex, String signatureBase64) {
        if (file == null || sha256Hex == null || !sha256Hex.matches("[0-9a-f]{64}") || signatureBase64 == null || signatureBase64.length() > 200) return false;
        byte[] signature;
        try { signature = Base64.getDecoder().decode(signatureBase64.trim()); } catch (IllegalArgumentException malformed) { return false; }
        if (signature.length != 64) return false;
        byte[] message = message(file, sha256Hex);
        for (String key : keys) {
            try {
                var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
                Signature check = Signature.getInstance("Ed25519");
                check.initVerify(publicKey);
                check.update(message);
                if (check.verify(signature)) return true;
            } catch (GeneralSecurityException | IllegalArgumentException unusable) { /* try the next key */ }
        }
        return false;
    }
}
