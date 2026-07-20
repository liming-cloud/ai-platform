package com.aiplatform.modelhub.application.query;

import com.aiplatform.modelhub.application.result.ModelConfigDetailResult;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;

/**
 * 模型配置查询仓储，服务 CQRS 查询侧，允许直接读取持久化视图并映射为应用查询结果。
 */
public interface ModelConfigQueryRepository {
    List<ModelConfigDetailResult> findDetailsByProviderId(TenantId tenantId, String providerId);
}
