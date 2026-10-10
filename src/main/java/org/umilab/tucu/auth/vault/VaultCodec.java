package org.umilab.tucu.auth.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.umilab.tucu.auth.crypto.CryptoService;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Serializes/encrypts and decrypts/deserializes the vault file:
 *
 * {
 *   "version": 1,
 *   "header": {
 *     "slots": [ { "type":"password", "kdf":"PBKDF2WithHmacSHA256",
 *                  "iterations":600000, "salt":"<b64>",
 *                  "nonce":"<b64>", "wrappedKey":"<b64+tag>" } ],
 *     "params": { "nonce":"<b64>", "tag":"<b64>" }
 *   },
 *   "db": "<b64 ciphertext>"
 * }
 *
 * Envelope encryption: a random DEK encrypts the db; a KEK derived from the
 * master password wraps the DEK into the password slot. Wrong password or
 * tampering surfaces as a GCM tag failure.
 */
public final class VaultCodec {

    public static final int FORMAT_VERSION = 1;

    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64D = Base64.getDecoder();

    private VaultCodec() {}

    /** An unlocked session: the DEK, the decrypted vault, and the header needed to re-encrypt. */
    public static final class Session {
        public final byte[] dek;
        public JsonObject slot;
        public final Vault vault;
        private Session(byte[] dek, JsonObject slot, Vault vault) {
            this.dek = dek;
            this.slot = slot;
            this.vault = vault;
        }
        public void wipe() {
            Arrays.fill(dek, (byte) 0);
            vault.wipe();
        }
    }

    /** Creates a new session with a fresh DEK wrapped under the password and an empty vault. */
    public static Session newSession(char[] password) throws GeneralSecurityException {
        byte[] dek = CryptoService.randomBytes(CryptoService.KEY_LENGTH);
        JsonObject slot = makePasswordSlot(password, dek);
        return new Session(dek, slot, new Vault());
    }

    /** Creates a new encrypted file body for an empty/new vault. */
    public static String create(char[] password, Vault vault) throws GeneralSecurityException {
        Session s = newSession(password);
        try {
            return encryptDb(s.dek, s.slot, vault);
        } finally {
            s.wipe();
        }
    }

    /** Opens an existing vault file. Throws GeneralSecurityException on wrong password/tamper. */
    public static Session open(String fileBody, char[] password)
            throws GeneralSecurityException {
        JsonObject root = JsonParser.parseString(fileBody).getAsJsonObject();
        JsonObject header = root.getAsJsonObject("header");
        JsonObject slot = findPasswordSlot(header);
        byte[] kek = null;
        try {
            kek = deriveKekFromSlot(password, slot);
            byte[] dek = CryptoService.decrypt(
                    kek,
                    B64D.decode(slot.get("wrappedKey").getAsString()),
                    B64D.decode(slot.get("nonce").getAsString()),
                    aad(slot));
            // verify DEK also opens the db
            byte[] plain = decryptDb(root, dek);
            Vault vault = deserializeDb(plain);
            Arrays.fill(plain, (byte) 0);
            return new Session(dek, slot, vault);
        } finally {
            if (kek != null) Arrays.fill(kek, (byte) 0);
        }
    }

    /** Re-encrypts the vault under the session's existing DEK. */
    public static String encryptDb(Session session, Vault vault) throws GeneralSecurityException {
        return encryptDb(session.dek, session.slot, vault);
    }

    /** Re-wraps the DEK under a new password and re-encrypts the db. */
    public static String rewrap(Session session, char[] newPassword, Vault vault)
            throws GeneralSecurityException {
        JsonObject slot = makePasswordSlot(newPassword, session.dek);
        session.slot = slot;
        return encryptDb(session.dek, slot, vault);
    }

    // ---- internals ----

    private static JsonObject makePasswordSlot(char[] password, byte[] dek)
            throws GeneralSecurityException {
        byte[] salt = CryptoService.randomBytes(CryptoService.SALT_LENGTH);
        byte[] kek = CryptoService.deriveKek(password, salt, CryptoService.KDF_ITERATIONS);
        byte[] nonce = CryptoService.randomBytes(CryptoService.NONCE_LENGTH);
        try {
            JsonObject slot = new JsonObject();
            slot.addProperty("type", "password");
            slot.addProperty("kdf", CryptoService.KDF_ALGORITHM);
            slot.addProperty("iterations", CryptoService.KDF_ITERATIONS);
            slot.addProperty("salt", B64.encodeToString(salt));
            slot.addProperty("nonce", B64.encodeToString(nonce));
            slot.addProperty("wrappedKey",
                    B64.encodeToString(CryptoService.encrypt(kek, dek, nonce, aad(slot))));
            return slot;
        } finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    private static JsonObject findPasswordSlot(JsonObject header) throws GeneralSecurityException {
        JsonArray slots = header.getAsJsonArray("slots");
        for (int i = 0; i < slots.size(); i++) {
            JsonObject s = slots.get(i).getAsJsonObject();
            if ("password".equals(s.get("type").getAsString())) {
                return s;
            }
        }
        throw new GeneralSecurityException("No password slot in vault header");
    }

    private static byte[] deriveKekFromSlot(char[] password, JsonObject slot)
            throws GeneralSecurityException {
        if (!CryptoService.KDF_ALGORITHM.equals(slot.get("kdf").getAsString())) {
            throw new GeneralSecurityException("Unsupported KDF: " + slot.get("kdf").getAsString());
        }
        return CryptoService.deriveKek(password,
                B64D.decode(slot.get("salt").getAsString()),
                slot.get("iterations").getAsInt());
    }

    /** AAD binds version + kdf + salt so header fields are authenticated. */
    private static byte[] aad(JsonObject slot) {
        String s = FORMAT_VERSION + "|" + slot.get("kdf").getAsString() + "|"
                + slot.get("iterations").getAsInt() + "|" + slot.get("salt").getAsString();
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String encryptDb(byte[] dek, JsonObject slot, Vault vault)
            throws GeneralSecurityException {
        byte[] plain = serializeDb(vault);
        byte[] nonce = CryptoService.randomBytes(CryptoService.NONCE_LENGTH);
        try {
            byte[] ct = CryptoService.encrypt(dek, plain, nonce, aad(slot));
            byte[] tag = Arrays.copyOfRange(ct, ct.length - 16, ct.length);

            JsonObject header = new JsonObject();
            JsonArray slots = new JsonArray();
            slots.add(slot);
            header.add("slots", slots);
            JsonObject params = new JsonObject();
            params.addProperty("nonce", B64.encodeToString(nonce));
            params.addProperty("tag", B64.encodeToString(tag));
            header.add("params", params);

            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT_VERSION);
            root.add("header", header);
            root.addProperty("db", B64.encodeToString(ct));
            return root.toString();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private static byte[] decryptDb(JsonObject root, byte[] dek) throws GeneralSecurityException {
        JsonObject header = root.getAsJsonObject("header");
        JsonObject slot = findPasswordSlot(header);
        JsonObject params = header.getAsJsonObject("params");
        return CryptoService.decrypt(dek,
                B64D.decode(root.get("db").getAsString()),
                B64D.decode(params.get("nonce").getAsString()),
                aad(slot));
    }

    private static byte[] serializeDb(Vault vault) {
        JsonObject db = new JsonObject();
        db.addProperty("version", 1);
        JsonArray entries = new JsonArray();
        for (VaultEntry e : vault.getEntries()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", e.getUuid());
            o.addProperty("name", e.getName());
            o.addProperty("issuer", e.getIssuer());
            o.addProperty("secret", Base32.encode(e.getSecret()));
            o.addProperty("period", e.getPeriod());
            o.addProperty("favorite", e.isFavorite());
            entries.add(o);
        }
        db.add("entries", entries);
        return db.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Vault deserializeDb(byte[] plain) {
        JsonObject db = JsonParser.parseString(new String(plain, StandardCharsets.UTF_8))
                .getAsJsonObject();
        Vault vault = new Vault();
        for (var el : db.getAsJsonArray("entries")) {
            JsonObject o = el.getAsJsonObject();
            vault.put(new VaultEntry(
                    o.get("uuid").getAsString(),
                    o.get("name").getAsString(),
                    o.get("issuer").getAsString(),
                    Base32.decode(o.get("secret").getAsString()),
                    o.has("period") ? o.get("period").getAsInt() : 30,
                    o.has("favorite") && o.get("favorite").getAsBoolean()));
        }
        return vault;
    }
}
