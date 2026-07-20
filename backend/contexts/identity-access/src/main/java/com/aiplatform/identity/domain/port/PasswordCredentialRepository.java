package com.aiplatform.identity.domain.port;

import com.aiplatform.identity.domain.PasswordCredential;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

public interface PasswordCredentialRepository {
    Optional<PasswordCredential> findByUserId(TenantId tenantId, String userId);
}
