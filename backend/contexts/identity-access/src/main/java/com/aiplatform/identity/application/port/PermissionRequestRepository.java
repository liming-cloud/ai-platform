package com.aiplatform.identity.application.port;

import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestPage;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestQuery;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

public interface PermissionRequestRepository {
    PermissionRequest save(PermissionRequest request);
    Optional<PermissionRequest> findById(TenantId tenantId, String requestId);
    PermissionRequestPage findPage(PermissionRequestQuery query);
}
