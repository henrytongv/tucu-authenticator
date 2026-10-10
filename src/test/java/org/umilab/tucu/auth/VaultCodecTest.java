package org.umilab.tucu.auth;

import org.umilab.tucu.auth.crypto.CryptoService;
import org.umilab.tucu.auth.vault.Base32;
import org.umilab.tucu.auth.vault.Vault;
import org.umilab.tucu.auth.vault.VaultCodec;
import org.umilab.tucu.auth.vault.VaultEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultCodecTest {

    private static final char[] PASSWORD = "Str0ng!Passw0rdExample".toCharArray();
    private static final int TEST_ITERATIONS = CryptoService.KDF_ITERATIONS;

    @Test
    void createOpenRoundTrip() throws Exception {
        Vault vault = new Vault();
        vault.put(new VaultEntry("uuid-1", "alice@example.com", "Example",
                Base32.decode("OE5QYFQG2K4IWGKG"), 30));

        String body = VaultCodec.create(PASSWORD, vault);
        assertTrue(body.contains("\"slots\""));
        assertTrue(body.contains("PBKDF2WithHmacSHA256"));

        VaultCodec.Session s = VaultCodec.open(body, PASSWORD);
        assertEquals(1, s.vault.getEntries().size());
        VaultEntry e = s.vault.getEntries().get(0);
        assertEquals("alice@example.com", e.getName());
        assertEquals("Example", e.getIssuer());
        assertEquals(30, e.getPeriod());
        s.wipe();
    }

    @Test
    void wrongPasswordFails() throws Exception {
        Vault vault = new Vault();
        vault.put(new VaultEntry("u", "n", "i", Base32.decode("OE5QYFQG2K4IWGKG"), 30));
        String body = VaultCodec.create(PASSWORD, vault);
        assertThrows(Exception.class,
                () -> VaultCodec.open(body, "WrongPassw0rd!Wrong".toCharArray()));
    }

    @Test
    void tamperedCiphertextFails() throws Exception {
        Vault vault = new Vault();
        vault.put(new VaultEntry("u", "n", "i", Base32.decode("OE5QYFQG2K4IWGKG"), 30));
        String body = VaultCodec.create(PASSWORD, vault);
        String tampered = body.replaceFirst("(.{500}).", "$1A");
        assertThrows(Exception.class, () -> VaultCodec.open(tampered, PASSWORD));
    }

    @Test
    void rewrapChangesPassword() throws Exception {
        Vault vault = new Vault();
        vault.put(new VaultEntry("u", "n", "i", Base32.decode("OE5QYFQG2K4IWGKG"), 30));
        VaultCodec.Session s = VaultCodec.newSession(PASSWORD);
        String body = VaultCodec.rewrap(s, "N3w!Str0ngerPassword".toCharArray(), vault);
        assertThrows(Exception.class, () -> VaultCodec.open(body, PASSWORD));
        VaultCodec.Session s2 = VaultCodec.open(body, "N3w!Str0ngerPassword".toCharArray());
        assertEquals(1, s2.vault.getEntries().size());
        s2.wipe();
        s.wipe();
    }

    @Test
    void favoritesRoundTrip() throws Exception {
        Vault vault = new Vault();
        vault.put(new VaultEntry("u1", "n", "i", Base32.decode("OE5QYFQG2K4IWGKG"), 30, true));
        vault.put(new VaultEntry("u2", "n2", "i2", Base32.decode("OE5QYFQG2K4IWGKG"), 30));
        String body = VaultCodec.create(PASSWORD, vault);
        VaultCodec.Session s = VaultCodec.open(body, PASSWORD);
        assertTrue(s.vault.get("u1").isFavorite());
        assertFalse(s.vault.get("u2").isFavorite());
        s.wipe();
    }

    @Test
    void legacyVaultWithoutFavoriteDefaultsFalse() throws Exception {
        // deserialize a db payload written before the favorite field existed
        var m = VaultCodec.class.getDeclaredMethod("deserializeDb", byte[].class);
        m.setAccessible(true);
        String legacyDb = "{\"version\":1,\"entries\":[{\"uuid\":\"u1\",\"name\":\"n\","
                + "\"issuer\":\"i\",\"secret\":\"OE5QYFQG2K4IWGKG\",\"period\":30}]}";
        Vault v = (Vault) m.invoke(null, legacyDb.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(v.get("u1").isFavorite());
        v.wipe();
    }

    @Test
    void kdfIterationsAreAdequate() {
        assertTrue(TEST_ITERATIONS >= 600_000,
                "PBKDF2 iterations must be >= 600k per OWASP");
    }
}
