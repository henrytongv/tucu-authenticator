package org.umilab.tucu.auth;

import org.umilab.tucu.auth.otp.Totp;
import org.umilab.tucu.auth.vault.Base32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotpTest {

    // RFC 6238 test secret: ASCII "12345678901234567890"
    private static final byte[] SECRET =
            Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

    @Test
    void rfc6238VectorsSha1() {
        // RFC 6238 8-digit values; our impl produces 6 digits = mod 1e6
        assertEquals("287082", Totp.code(SECRET, 30, 59_000L));           // 94287082
        assertEquals("081804", Totp.code(SECRET, 30, 1_111_111_109_000L)); // 07081804
        assertEquals("005924", Totp.code(SECRET, 30, 1_234_567_890_000L)); // 89005924
    }

    @Test
    void secondsRemaining() {
        assertEquals(1, Totp.secondsRemaining(30, 29_000L));
        assertEquals(30, Totp.secondsRemaining(30, 30_000L));
        assertEquals(15, Totp.secondsRemaining(30, 45_000L));
    }

    @Test
    void base32RoundTrip() {
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertArrayEquals(data, Base32.decode(Base32.encode(data)));
    }
}
