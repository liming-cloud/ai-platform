package org.fujay.ai.platform.model.application.port;

public interface ProviderSecretPort {

    String encrypt(String plaintext);

    String last4(String plaintext);
}
