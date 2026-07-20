package com.aiplatform.modelhub.application.query;

import com.aiplatform.modelhub.application.result.ModelProviderDetailResult;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;

/**
 * 模型供应商查询仓储，CQRS 查询侧允许直接读取数据库投影并返回应用查询结果。
 */
public interface ModelProviderQueryRepository {
    List<ModelProviderDetailResult> findAllDetails(TenantId tenantId);
}
