package org.umilab.tucu.auth.crypto;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Master password policy:
 *   - minimum 16 characters
 *   - at least one uppercase letter
 *   - at least one non-alphanumeric character
 *   - must not appear in the bundled common-password blocklist
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 16;

    private static volatile Set<String> blocklist;

    private PasswordPolicy() {}

    /** Returns null if acceptable, otherwise a message-bundle key (see i18n/messages). */
    public static String validate(char[] password) {
        if (password == null || password.length < MIN_LENGTH) {
            return "pw.err.short";
        }
        boolean upper = false, nonAlnum = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) upper = true;
            if (!Character.isLetterOrDigit(c)) nonAlnum = true;
        }
        if (!upper) {
            return "pw.err.upper";
        }
        if (!nonAlnum) {
            return "pw.err.symbol";
        }
        if (blocklist().contains(new String(password).toLowerCase(Locale.ROOT))) {
            return "pw.err.common";
        }
        return null;
    }

    /** Rough strength score 0-4 for the strength meter. */
    public static int strength(char[] password) {
        if (password == null) return 0;
        int score = 0;
        if (password.length >= 12) score++;
        if (password.length >= MIN_LENGTH) score++;
        boolean upper = false, digit = false, symbol = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else if (!Character.isLetterOrDigit(c)) symbol = true;
        }
        if (upper && digit) score++;
        if (symbol) score++;
        if (blocklist().contains(new String(password).toLowerCase(Locale.ROOT))) score = 0;
        return Math.min(score, 4);
    }

    private static Set<String> blocklist() {
        Set<String> bl = blocklist;
        if (bl == null) {
            bl = new HashSet<>();
            try (InputStream in = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt")) {
                if (in != null) {
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            line = line.trim().toLowerCase(Locale.ROOT);
                            if (!line.isEmpty() && !line.startsWith("#")) bl.add(line);
                        }
                    }
                }
            } catch (Exception ignored) {
                // blocklist is best-effort; policy length rules still apply
            }
            blocklist = bl;
        }
        return bl;
    }
}
