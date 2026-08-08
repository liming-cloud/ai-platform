package com.aiplatform.foundation.evaluationoperations.domain.release.repository;

import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 不可变发布决定根的同租户集合边界。 */
public interface ReleaseDecisionRepository {

    void save(ReleaseDecision decision);

    Optional<ReleaseDecision> findById(TenantId tenantId, ReleaseDecisionId id);
}
