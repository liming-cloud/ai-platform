package com.aiplatform.foundation.modelruntime.application.provider.port.out;

import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryHistoryPage;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 为控制台提供租户隔离的模型发现历史读取端口。 */
public interface ProviderModelDiscoveryHistoryReader {

    /** 按发现时间倒序读取指定连接的服务端分页历史。 */
    ProviderModelDiscoveryHistoryPage findPage(
            TenantId tenantId,
            ProviderConnectionId providerConnectionId,
            long pageNumber,
            long pageSize);
}
