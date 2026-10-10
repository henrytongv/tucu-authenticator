package org.umilab.tucu.auth.vault;

import java.util.Arrays;

/** RFC 4648 base32 (uppercase, optional padding/whitespace tolerated on decode). */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {}

    public static byte[] decode(String s) {
        StringBuilder clean = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '=' || Character.isWhitespace(c)) continue;
            clean.append(Character.toUpperCase(c));
        }
        int len = clean.length();
        byte[] out = new byte[len * 5 / 8];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (int i = 0; i < len; i++) {
            int v = ALPHABET.indexOf(clean.charAt(i));
            if (v < 0) throw new IllegalArgumentException("Invalid base32 character");
            buffer = (buffer << 5) | v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[idx++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return idx == out.length ? out : Arrays.copyOf(out, idx);
    }

    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }
}
