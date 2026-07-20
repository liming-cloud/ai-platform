package com.aiplatform.identity.domain.port;

import com.aiplatform.identity.domain.EnterpriseUser;

public interface PasswordVerifier {
    boolean matches(EnterpriseUser user, String rawPassword);

    void performDummyVerification(String rawPassword);
}
