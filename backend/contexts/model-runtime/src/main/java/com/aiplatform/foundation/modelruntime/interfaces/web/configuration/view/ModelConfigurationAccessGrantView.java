package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import java.time.Instant;

/**
 * 模型配置授权状态视图，不包含模型凭据材料。
 */
public record ModelConfigurationAccessGrantView(
        String id, String modelConfigurationId, String tenantId, String membershipId,
        String status, long version, Instant createdAt, Instant revokedAt) {

    public static ModelConfigurationAccessGrantView from(ModelConfigurationAccessGrant grant) {
        return new ModelConfigurationAccessGrantView(
                grant.id().toString(), grant.modelConfigurationId().toString(),
                grant.granteeTenantId().toString(),
                grant.granteeMembershipId() == null ? null : grant.granteeMembershipId().toString(),
                grant.status().name(), grant.version(), grant.createdAt(), grant.revokedAt());
    }
}
