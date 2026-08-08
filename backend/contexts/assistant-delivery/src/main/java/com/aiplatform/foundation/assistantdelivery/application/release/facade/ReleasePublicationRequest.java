package com.aiplatform.foundation.assistantdelivery.application.release.facade;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 交给评测上下文复核的冻结发布目标；不携带助手实体、评测正文或身份凭据。
 */
public record ReleasePublicationRequest(
        TenantId tenantId,
        long assistantVersionId,
        long assistantVersionRevision,
        long releaseDecisionId) implements ValueObject {

    public ReleasePublicationRequest {
        tenantId = Objects.requireNonNull(tenantId, "发布租户不能为空");
        if (assistantVersionId <= 0L || assistantVersionRevision < 0L || releaseDecisionId <= 0L) {
            throw new IllegalArgumentException("发布授权目标格式不正确");
        }
    }
}
