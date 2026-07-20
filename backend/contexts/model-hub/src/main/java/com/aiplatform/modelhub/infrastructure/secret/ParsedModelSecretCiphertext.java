package com.aiplatform.modelhub.infrastructure.secret;

record ParsedModelSecretCiphertext(String keyId, byte[] iv, byte[] encrypted) {
}
