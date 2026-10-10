package org.umilab.tucu.auth.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Imports an Aegis vault export, plaintext or password-encrypted.
 * Only TOTP/SHA1/6-digit entries are imported; anything else is skipped
 * and reported. Encrypted exports are decrypted once with the export
 * password, which is used only for the import and never stored.
 */
public final class AegisImporter {

    public record Result(List<VaultEntry> entries, int skipped) {}

    /** True when the export's header carries slots, i.e. "db" is ciphertext. */
    public static boolean isEncrypted(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject header = root.has("header") && root.get("header").isJsonObject()
                    ? root.getAsJsonObject("header") : null;
            return header != null
                    && header.has("slots")
                    && header.get("slots").isJsonArray()
                    && !header.getAsJsonArray("slots").isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Parses a plaintext (unencrypted) Aegis export. */
    public Result parse(String json) throws ImportException {
        JsonObject root = parseRoot(json);
        if (isEncrypted(root)) {
            throw new ImportException("import.err.encrypted");
        }
        JsonObject db = root.has("db") && root.get("db").isJsonObject()
                ? root.getAsJsonObject("db") : null;
        if (db == null || !db.has("entries")) {
            throw new ImportException("import.err.format");
        }
        return parseDb(db);
    }

    /**
     * Decrypts a password-encrypted Aegis export and parses its entries.
     * The password bytes are wiped inside the decryptor.
     */
    public Result parseEncrypted(String json, char[] password) throws ImportException {
        JsonObject root = parseRoot(json);
        JsonObject db = AegisDecryptor.decryptDb(root, password);
        return parseDb(db);
    }

    private static JsonObject parseRoot(String json) throws ImportException {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new ImportException("import.err.json");
        }
    }

    private static boolean isEncrypted(JsonObject root) {
        JsonObject header = root.has("header") && root.get("header").isJsonObject()
                ? root.getAsJsonObject("header") : null;
        return header != null
                && header.has("slots")
                && header.get("slots").isJsonArray()
                && !header.getAsJsonArray("slots").isEmpty();
    }

    private Result parseDb(JsonObject db) {
        List<VaultEntry> entries = new ArrayList<>();
        int skipped = 0;
        JsonArray arr = db.has("entries") && db.get("entries").isJsonArray()
                ? db.getAsJsonArray("entries") : new JsonArray();
        for (var el : arr) {
            JsonObject e = el.getAsJsonObject();
            String type = opt(e, "type");
            JsonObject info = e.has("info") ? e.getAsJsonObject("info") : new JsonObject();
            String algo = opt(info, "algo");
            int digits = optInt(info, "digits", 6);
            if (!"totp".equals(type) || !"SHA1".equalsIgnoreCase(algo) || digits != 6) {
                skipped++;
                continue;
            }
            try {
                String secret = info.get("secret").getAsString();
                byte[] decoded = Base32.decode(secret);
                if (decoded.length == 0) throw new IllegalArgumentException();
                entries.add(new VaultEntry(
                        opt(e, "uuid"),
                        opt(e, "name"),
                        opt(e, "issuer"),
                        decoded,
                        optInt(info, "period", 30),
                        e.has("favorite") && !e.get("favorite").isJsonNull()
                                && e.get("favorite").getAsBoolean()));
            } catch (Exception ex) {
                skipped++;
            }
        }
        return new Result(entries, skipped);
    }

    private static String opt(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static int optInt(JsonObject o, String key, int def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static class ImportException extends Exception {
        private final String key;
        /** @param key message-bundle key the UI resolves via Messages.get() */
        public ImportException(String key) { super(key); this.key = key; }
        public String key() { return key; }
    }
}
