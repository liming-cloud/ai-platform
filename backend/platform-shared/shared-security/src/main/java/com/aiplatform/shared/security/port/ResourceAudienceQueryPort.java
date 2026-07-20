package com.aiplatform.shared.security.port;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Set;

public interface ResourceAudienceQueryPort {
    Set<String> findAllowedUserIds(
            TenantId tenantId,
            String resourceType,
            String resourceId,
            PermissionCode permissionCode
    );
}
