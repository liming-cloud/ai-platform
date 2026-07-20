package com.aiplatform.identity.infrastructure.security.password.model;

public record StoredPasswordHash(int iterations, byte[] salt, byte[] expectedHash) {
}
