package org.umilab.tucu.auth;

import org.umilab.tucu.auth.vault.AegisImporter;
import org.umilab.tucu.auth.vault.VaultEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AegisImporterTest {

    @Test
    void parsesPlaintextExport() throws Exception {
        Path example = Path.of("..", "aegis_work", "aegis-example-vault-export.json");
        if (!Files.exists(example)) {
            // fall back to inline sample if run outside the workspace
            String json = """
                {"version":1,"header":{"slots":null,"params":null},"db":{"version":3,
                "entries":[{"type":"totp","uuid":"u1","name":"n","issuer":"i","note":"",
                "favorite":false,"icon":null,
                "info":{"secret":"OE5QYFQG2K4IWGKG","algo":"SHA1","digits":6,"period":30},
                "groups":[]}],"groups":[],"icons_optimized":true}}""";
            AegisImporter.Result r = new AegisImporter().parse(json);
            assertEquals(1, r.entries().size());
            assertEquals(0, r.skipped());
            return;
        }
        AegisImporter.Result r = new AegisImporter().parse(Files.readString(example));
        assertEquals(2, r.entries().size());
        assertEquals(0, r.skipped());
        List<VaultEntry> entries = r.entries();
        assertEquals("Microsoft", entries.get(0).getIssuer());
        assertEquals("Google", entries.get(1).getIssuer());
    }

    @Test
    void skipsUnsupportedEntries() {
        String json = """
            {"version":1,"header":{"slots":null,"params":null},"db":{"version":3,
            "entries":[
              {"type":"totp","uuid":"a","name":"n","issuer":"i",
               "info":{"secret":"OE5QYFQG2K4IWGKG","algo":"SHA256","digits":6,"period":30}},
              {"type":"hotp","uuid":"b","name":"n","issuer":"i",
               "info":{"secret":"OE5QYFQG2K4IWGKG","algo":"SHA1","digits":6,"counter":0}},
              {"type":"totp","uuid":"c","name":"n","issuer":"i",
               "info":{"secret":"OE5QYFQG2K4IWGKG","algo":"SHA1","digits":8,"period":30}},
              {"type":"totp","uuid":"d","name":"n","issuer":"i",
               "info":{"secret":"OE5QYFQG2K4IWGKG","algo":"SHA1","digits":6,"period":60}}
            ],"groups":[]}}""";
        try {
            AegisImporter.Result r = new AegisImporter().parse(json);
            assertEquals(1, r.entries().size());
            assertEquals(3, r.skipped());
            assertEquals(60, r.entries().get(0).getPeriod());
        } catch (AegisImporter.ImportException e) {
            fail(e);
        }
    }

    @Test
    void rejectsEncryptedExport() {
        String json = """
            {"version":1,"header":{"slots":[{"type":"password"}],"params":{}},"db":{}}""";
        assertThrows(AegisImporter.ImportException.class,
                () -> new AegisImporter().parse(json));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(AegisImporter.ImportException.class,
                () -> new AegisImporter().parse("not json at all"));
    }

    // ---- encrypted exports ----

    private static String resource(String name) throws Exception {
        try (var in = AegisImporterTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, "missing test resource " + name);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void detectsEncryptedExport() throws Exception {
        assertTrue(AegisImporter.isEncrypted(resource("aegis_encrypted.json")));
        assertFalse(AegisImporter.isEncrypted(resource("aegis_plain.json")));
        assertFalse(AegisImporter.isEncrypted("not json"));
    }

    @Test
    void decryptsRealAegisExport() throws Exception {
        // fixture ships with Aegis's own test suite; export password is "test"
        AegisImporter.Result r =
                new AegisImporter().parseEncrypted(resource("aegis_encrypted.json"),
                        "test".toCharArray());
        assertTrue(r.entries().size() > 0, "expected decrypted entries");
        for (VaultEntry e : r.entries()) {
            assertFalse(e.getUuid().isBlank());
            assertTrue(e.getSecret().length > 0);
            e.wipe();
        }
    }

    @Test
    void wrongExportPasswordRejected() throws Exception {
        AegisImporter.ImportException ex = assertThrows(AegisImporter.ImportException.class,
                () -> new AegisImporter().parseEncrypted(resource("aegis_encrypted.json"),
                        "wrongpassword".toCharArray()));
        assertEquals("import.err.wrongpw", ex.key());
    }

    @Test
    void mapsFavoriteFlag() throws Exception {
        String json = """
            {"version":1,"header":{"slots":null,"params":null},"db":{"version":3,
            "entries":[{"type":"totp","uuid":"u1","name":"n","issuer":"i","favorite":true,
            "info":{"secret":"OE5QYFQG2K4IWGKG","algo":"SHA1","digits":6,"period":30}}]}}""";
        AegisImporter.Result r = new AegisImporter().parse(json);
        assertEquals(1, r.entries().size());
        assertTrue(r.entries().get(0).isFavorite());
    }
}
