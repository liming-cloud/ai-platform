package com.aiplatform.shared.security.port;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.model.ResourceAclSubject;
import java.util.Set;

public interface ResourceAclCommandPort {
    void grantAllowingSubject(
            TenantId tenantId,
            String resourceType,
            String resourceId,
            PermissionCode permissionCode,
            ResourceAclSubject subject,
            String actorId
    );

    void replaceAllowingSubjects(
            TenantId tenantId,
            String resourceType,
            String resourceId,
            PermissionCode permissionCode,
            Set<ResourceAclSubject> subjects,
            String actorId
    );
}
