package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.TenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLoginAttemptEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLoginAttemptMapper;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 行锁使同一租户与主体的失败计数、锁定和成功清除串行化。 */
public class PostgresTenantLoginAttemptStore implements TenantLoginAttemptStore {
    private final TenantLoginAttemptMapper mapper;

    public PostgresTenantLoginAttemptStore(TenantLoginAttemptMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "失败观察映射器不能为空");
    }

    @Override
    public boolean isLocked(TenantId tenantId, SubjectId subjectId, Instant now) {
        var until = mapper.findLockedUntil(tenantId.value(), subjectId.value());
        return until != null && until.isAfter(now);
    }

    @Override
    @Transactional
    public void recordFailure(TenantId tenantId, SubjectId subjectId, TenantLoginSecurityPolicy policy, Instant now) {
        if (!policy.tenantId().equals(tenantId)) throw new IllegalArgumentException("策略与租户不一致");
        mapper.ensureRow(tenantId.value(), subjectId.value(), now);
        var state = mapper.lock(tenantId.value(), subjectId.value());
        if (state.lockedUntil() != null && state.lockedUntil().isAfter(now)) return;
        var fresh = state.lockedUntil() != null
                || !now.isBefore(state.windowStartedAt().plus(policy.observationMinutes(), ChronoUnit.MINUTES));
        int failures = fresh ? 1 : state.failures() + 1;
        var started = fresh ? now : state.windowStartedAt();
        var until = failures >= policy.failedAttempts()
                ? now.plus(policy.lockMinutes(), ChronoUnit.MINUTES) : null;
        mapper.replace(tenantId.value(), subjectId.value(), started, failures, until);
    }

    @Override
    @Transactional
    public boolean clearIfUnlocked(TenantId tenantId, SubjectId subjectId, Instant now) {
        var state = mapper.lock(tenantId.value(), subjectId.value());
        if (state == null) return true;
        if (state.lockedUntil() != null && state.lockedUntil().isAfter(now)) return false;
        mapper.delete(tenantId.value(), subjectId.value());
        return true;
    }
}
