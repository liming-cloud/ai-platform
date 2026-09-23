package com.aiplatform.foundation.trustedquery.application.operations.facade;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsSummaryFacts;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 向运营总览提供查询运行计数；禁止返回问题、回答、引用、请求成员或厂商调用详情。
 */
public interface QueryOverviewFactSource {

    QueryOperationsSummaryFacts platformSummary(Instant from, Instant to);

    QueryOperationsSummaryFacts tenantSummary(TenantId tenantId, Instant from, Instant to);

    Map<TenantId, QueryOperationsSummaryFacts> tenantSummaries(
            List<TenantId> tenantIds,
            Instant from,
            Instant to);

    /** 返回存在失败查询或达到日配额八成的租户集合，仅用于后端风险去重。 */
    Set<TenantId> attentionTenants(Instant from, Instant to);
}
