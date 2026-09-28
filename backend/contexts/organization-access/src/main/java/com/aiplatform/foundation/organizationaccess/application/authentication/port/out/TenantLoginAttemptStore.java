package com.aiplatform.foundation.organizationaccess.application.authentication.port.out;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;

/**
 * 租户和主体范围的原子失败观察；锁定期间正确口令也不能清除锁。
 */
public interface TenantLoginAttemptStore {
    /** 查询目标身份当前是否仍在锁定期。 */
    boolean isLocked(TenantId tenantId, SubjectId subjectId, Instant now);

    /** 原子累计失败次数，并在达到策略阈值时开始锁定。 */
    void recordFailure(TenantId tenantId, SubjectId subjectId, TenantLoginSecurityPolicy policy, Instant now);

    /** 仅在锁定期已结束时清除观察事实，避免正确口令提前解锁。 */
    boolean clearIfUnlocked(TenantId tenantId, SubjectId subjectId, Instant now);
}
