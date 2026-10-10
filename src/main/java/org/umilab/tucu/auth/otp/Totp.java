package org.umilab.tucu.auth.otp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/** RFC 6238 TOTP, SHA-1, 6 digits, configurable period. */
public final class Totp {

    private Totp() {}

    public static String code(byte[] secret, int periodSeconds, long epochMillis) {
        long counter = Math.floorDiv(epochMillis / 1000L, periodSeconds);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Seconds until the current code rotates. */
    public static int secondsRemaining(int periodSeconds, long epochMillis) {
        return periodSeconds - (int) (epochMillis / 1000L % periodSeconds);
    }
}
