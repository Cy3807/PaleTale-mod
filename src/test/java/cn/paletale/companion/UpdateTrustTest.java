package cn.paletale.companion;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** The release signature made by scripts/mod-sign.mjs (Node) must verify here (Java), and nothing else may. */
final class UpdateTrustTest {
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run() {
        try (var in = UpdateTrustTest.class.getResourceAsStream("/update-signature-v1.json")) {
            var fx = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String key = fx.get("publicKey").getAsString(), file = fx.get("file").getAsString(), sha = fx.get("sha256").getAsString(), sig = fx.get("signature").getAsString();
            List<String> keys = List.of(key);
            require(UpdateTrust.verify(keys, file, sha, sig), "A signature made by the Node tool verifies in Java");
            require(!UpdateTrust.verify(keys, "Paletale-9.9.9-1.21.8.jar", sha, sig), "The signature is bound to the file name (no moving it to another game version)");
            require(!UpdateTrust.verify(keys, file, "0".repeat(64), sig), "A different jar digest is refused");
            require(!UpdateTrust.verify(keys, file, sha.toUpperCase(java.util.Locale.ROOT), sig), "Digest must be lower-case hex");
            require(!UpdateTrust.verify(UpdateTrust.RELEASE_KEYS, file, sha, sig), "A signature from another key is refused by the built-in release key");
            require(!UpdateTrust.verify(keys, file, sha, null) && !UpdateTrust.verify(keys, file, sha, "") && !UpdateTrust.verify(keys, file, sha, "not base64!!"), "Missing or malformed signatures are refused");
            byte[] raw = Base64.getDecoder().decode(sig); raw[0] ^= 1;
            require(!UpdateTrust.verify(keys, file, sha, Base64.getEncoder().encodeToString(raw)), "A flipped bit is refused");
            require(!UpdateTrust.verify(keys, file, sha, Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(raw, 63))), "A short signature is refused");
            require(!UpdateTrust.verify(List.of("AAAA", "not a key"), file, sha, sig), "Unusable keys never accept");
            require(!UpdateTrust.verify(List.of(), file, sha, sig), "No keys, no trust");
            System.out.println("PaleTale update signature checks passed: 10");
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
}
