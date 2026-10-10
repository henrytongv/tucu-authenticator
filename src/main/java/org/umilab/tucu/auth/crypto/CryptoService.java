package org.umilab.tucu.auth.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Pure-JDK cryptography for the vault:
 *   PBKDF2WithHmacSHA256 (600k iterations) -> KEK
 *   KEK wraps a random 256-bit DEK (AES-256-GCM)
 *   DEK encrypts the vault database (AES-256-GCM)
 * AAD binds the vault header fields so they cannot be tampered with.
 */
public final class CryptoService {

    public static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int KDF_ITERATIONS = 600_000;
    public static final int SALT_LENGTH = 32;
    public static final int NONCE_LENGTH = 12;
    public static final int KEY_LENGTH = 32;      // 256-bit
    public static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoService() {}

    public static byte[] randomBytes(int length) {
        byte[] b = new byte[length];
        RANDOM.nextBytes(b);
        return b;
    }

    /** Derives a 256-bit KEK from a password. Wipes the intermediate key bytes. */
    public static byte[] deriveKek(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH * 8);
        byte[] kek = null;
        try {
            kek = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).getEncoded();
            return Arrays.copyOf(kek, kek.length);
        } finally {
            spec.clearPassword();
            if (kek != null) {
                Arrays.fill(kek, (byte) 0);
            }
        }
    }

    /** Returns ciphertext||tag. */
    public static byte[] encrypt(byte[] key, byte[] plaintext, byte[] nonce, byte[] aad)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypts ciphertext||tag. Throws GeneralSecurityException (AEADBadTagException)
     * if the key is wrong or the data was tampered with.
     */
    public static byte[] decrypt(byte[] key, byte[] ciphertextAndTag, byte[] nonce, byte[] aad)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(ciphertextAndTag);
    }
}
