package com.aiplatform.identity.domain.port;

import com.aiplatform.identity.domain.LoginSession;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

public interface LoginSessionRepository {
    Optional<LoginSession> findActiveByToken(TenantId tenantId, String tokenValue);

    void revokeByToken(TenantId tenantId, String tokenValue);
}
