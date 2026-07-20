package com.aiplatform.identity.application.port;

public interface PasswordHashEncoder {
    String encode(String rawPassword);
}
