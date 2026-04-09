package com.xg.platform.api.skill;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class SkillSecretCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public SkillSecretCrypto(String base64Key) {
        byte[] decoded = decodeKey(base64Key);
        this.keySpec = new SecretKeySpec(decoded, "AES");
    }

    public EncryptedPayload encrypt(byte[] plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new EncryptedPayload(cipher.doFinal(plaintext), iv);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt skill secrets", exception);
        }
    }

    public byte[] decrypt(byte[] ciphertext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to decrypt skill secrets", exception);
        }
    }

    private byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("platform.skills.secret-encryption-key must not be blank");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("platform.skills.secret-encryption-key must be valid base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("platform.skills.secret-encryption-key must decode to 32 bytes");
        }
        return Arrays.copyOf(decoded, decoded.length);
    }

    public record EncryptedPayload(byte[] ciphertext, byte[] iv) {
    }
}
