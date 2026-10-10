package org.umilab.tucu.auth.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bouncycastle.crypto.generators.SCrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Decrypts an Aegis "encrypted" vault export. Mirrors Aegis's own chain:
 *   password -> scrypt(N,r,p,salt) -> 32-byte KEK
 *   KEK unwraps the slot's master key (AES-GCM, tag appended)
 *   master key decrypts "db" (AES-GCM, tag appended) -> vault JSON
 *
 * Slot types: 0 raw, 1 password, 2 biometric (Android keystore — cannot
 * work on desktop; rejected). All slot/header scalars are hex; "db" is
 * base64. The supplied password is used only here and never stored.
 */
final class AegisDecryptor {

    private static final int SLOT_PASSWORD = 1;
    private static final int KEY_LEN = 32;
    private static final int GCM_TAG_BITS = 128;

    private AegisDecryptor() {}

    /** Returns the decrypted "db" object of an encrypted Aegis export. */
    static JsonObject decryptDb(JsonObject root, char[] password)
            throws AegisImporter.ImportException {
        JsonObject header = root.has("header") && root.get("header").isJsonObject()
                ? root.getAsJsonObject("header") : null;
        if (header == null || !header.has("slots") || !header.get("slots").isJsonArray()) {
            throw new AegisImporter.ImportException("import.err.format");
        }
        JsonObject slot = null;
        JsonArray slots = header.getAsJsonArray("slots");
        for (var el : slots) {
            JsonObject s = el.getAsJsonObject();
            if (s.has("type") && s.get("type").getAsInt() == SLOT_PASSWORD) {
                slot = s;
                break;
            }
        }
        if (slot == null) {
            // e.g. biometric-only export; no password slot to try
            throw new AegisImporter.ImportException("import.err.noPasswordSlot");
        }

        byte[] pwBytes = null;
        byte[] kek = null;
        byte[] masterKey = null;
        try {
            byte[] salt = hex(slot.get("salt").getAsString());
            int n = slot.get("n").getAsInt();
            int r = slot.get("r").getAsInt();
            int p = slot.get("p").getAsInt();
            JsonObject keyParams = slot.getAsJsonObject("key_params");
            byte[] keyNonce = hex(keyParams.get("nonce").getAsString());
            byte[] keyTag = hex(keyParams.get("tag").getAsString());
            byte[] wrappedKey = hex(slot.get("key").getAsString());

            pwBytes = toBytes(password);
            kek = SCrypt.generate(pwBytes, salt, n, r, p, KEY_LEN);
            masterKey = gcmDecrypt(kek, concat(wrappedKey, keyTag), keyNonce);

            JsonObject params = header.getAsJsonObject("params");
            byte[] dbNonce = hex(params.get("nonce").getAsString());
            byte[] dbTag = hex(params.get("tag").getAsString());
            byte[] dbCt = Base64.getDecoder().decode(root.get("db").getAsString());
            byte[] plain = gcmDecrypt(masterKey, concat(dbCt, dbTag), dbNonce);
            try {
                return JsonParser.parseString(new String(plain, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (javax.crypto.AEADBadTagException e) {
            throw new AegisImporter.ImportException("import.err.wrongpw");
        } catch (Exception e) {
            // malformed hex/base64/JSON or unexpected crypto failure
            throw new AegisImporter.ImportException("import.err.format");
        } finally {
            if (pwBytes != null) Arrays.fill(pwBytes, (byte) 0);
            if (kek != null) Arrays.fill(kek, (byte) 0);
            if (masterKey != null) Arrays.fill(masterKey, (byte) 0);
        }
    }

    private static byte[] gcmDecrypt(byte[] key, byte[] ctWithTag, byte[] nonce)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return c.doFinal(ctWithTag);
    }

    private static byte[] toBytes(char[] chars) {
        ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] b = Arrays.copyOf(buf.array(), buf.limit());
        Arrays.fill(buf.array(), (byte) 0);
        return b;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return out;
    }
}
