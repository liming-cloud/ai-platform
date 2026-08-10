package com.aiplatform.foundation.modelruntime.application.provider.port.out;

import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryCandidatePage;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;

/** 读取指定连接当前修订且尚未过期的发现候选。 */
public interface ProviderModelDiscoveryCandidateReader {

    ProviderModelDiscoveryCandidatePage findCurrentCandidates(
            TenantId tenantId,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            long pageNumber,
            long pageSize,
            Instant now);
}
