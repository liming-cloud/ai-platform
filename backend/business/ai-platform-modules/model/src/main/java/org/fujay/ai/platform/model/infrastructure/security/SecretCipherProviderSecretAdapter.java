package org.fujay.ai.platform.model.infrastructure.security;

import org.fujay.ai.platform.model.application.port.ProviderSecretPort;
import org.fujay.ai.platform.security.SecretCipher;
import org.springframework.stereotype.Component;

@Component
public class SecretCipherProviderSecretAdapter implements ProviderSecretPort {

    private final SecretCipher secretCipher;

    public SecretCipherProviderSecretAdapter(SecretCipher secretCipher) {
        this.secretCipher = secretCipher;
    }

    @Override
    public String encrypt(String plaintext) {
        return secretCipher.encrypt(plaintext);
    }

    @Override
    public String last4(String plaintext) {
        return secretCipher.last4(plaintext);
    }
}
