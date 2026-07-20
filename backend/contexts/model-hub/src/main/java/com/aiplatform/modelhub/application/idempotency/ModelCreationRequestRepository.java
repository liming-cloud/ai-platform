package com.aiplatform.modelhub.application.idempotency;

import com.aiplatform.shared.kernel.TenantId;

/** PostgreSQL-backed claim/complete seam for idempotent ModelHub creation. */
public interface ModelCreationRequestRepository {
    ModelCreationRequestClaim claim(
            TenantId tenantId,
            ModelCreationResourceType resourceType,
            String requestId,
            String requestDigest
    );

    void complete(
            TenantId tenantId,
            ModelCreationResourceType resourceType,
            String requestId,
            String requestDigest,
            ModelCreationResultSnapshot result
    );
}
