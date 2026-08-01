package com.aiplatform.foundation.trustedquery.application.diagnostic.port.out;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryDiagnosticFact;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import java.util.Optional;

/**
 * 按可信租户读取已经随查询终态固化的最小诊断事实。
 */
public interface QueryDiagnosticFactLookup {

    Optional<QueryDiagnosticFact> find(TenantId tenantId, QueryRunId queryRunId);
}
