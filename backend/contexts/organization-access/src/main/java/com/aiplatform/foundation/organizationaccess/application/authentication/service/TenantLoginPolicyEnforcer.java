package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.TenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** 两条租户口令入口共用的策略执行边界；平台登录不经过此服务。 */
public final class TenantLoginPolicyEnforcer {
    private final TenantLoginSecurityPolicyStore policies;
    private final TenantLoginAttemptStore attempts;
    private final Clock clock;

    public TenantLoginPolicyEnforcer(TenantLoginSecurityPolicyStore policies,
            TenantLoginAttemptStore attempts, Clock clock) {
        this.policies = Objects.requireNonNull(policies, "登录策略存储不能为空");
        this.attempts = Objects.requireNonNull(attempts, "失败观察存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时间源不能为空");
    }

    /**
     * 对已验证成员资格执行锁定与失败观察，返回实际租户会话时长。
     * @throws InvalidCredentialsException 错误口令或活动锁定均返回统一认证失败。
     */
    public Duration afterPasswordCheck(TenantId tenantId, SubjectId subjectId, boolean matched) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(subjectId, "主体标识不能为空");
        var policy = policies.find(tenantId.value())
                .orElseThrow(() -> new IllegalStateException("租户登录策略缺失"));
        var now = clock.instant();
        if (attempts.isLocked(tenantId, subjectId, now)) throw new InvalidCredentialsException();
        if (!matched) {
            attempts.recordFailure(tenantId, subjectId, policy, now);
            throw new InvalidCredentialsException();
        }
        if (!attempts.clearIfUnlocked(tenantId, subjectId, now)) throw new InvalidCredentialsException();
        return policy.sessionDuration();
    }
}
