package com.xg.platform.api.skill;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSecretCryptoTest {

    private static final String BASE64_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptsAndDecryptsRoundTrip() {
        SkillSecretCrypto crypto = new SkillSecretCrypto(BASE64_KEY);

        SkillSecretCrypto.EncryptedPayload encryptedPayload = crypto.encrypt("secret-value".getBytes(StandardCharsets.UTF_8));
        byte[] decrypted = crypto.decrypt(encryptedPayload.ciphertext(), encryptedPayload.iv());

        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("secret-value");
    }

    @Test
    void rejectsDecryptWithWrongKey() {
        SkillSecretCrypto crypto = new SkillSecretCrypto(BASE64_KEY);
        SkillSecretCrypto wrongCrypto = new SkillSecretCrypto(
                Base64.getEncoder().encodeToString("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8))
        );

        SkillSecretCrypto.EncryptedPayload encryptedPayload = crypto.encrypt("secret-value".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> wrongCrypto.decrypt(encryptedPayload.ciphertext(), encryptedPayload.iv()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }
}
