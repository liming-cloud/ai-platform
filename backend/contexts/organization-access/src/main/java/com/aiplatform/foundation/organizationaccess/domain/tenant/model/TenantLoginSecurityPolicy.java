package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.util.Objects;

/**
 * 租户独立登录策略。三个可编辑值不包含隔离、授权、凭据保护等不可关闭的安全底线。
 *
 * @throws IllegalArgumentException 当任一数值超出已接受的产品范围或版本为负时拒绝。
 */
public record TenantLoginSecurityPolicy(TenantId tenantId, int sessionHours, int failedAttempts,
        int lockMinutes, long version) {
    public TenantLoginSecurityPolicy {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (sessionHours < 1 || sessionHours > 24) {
            throw new IllegalArgumentException("会话有效期必须在 1～24 小时之间");
        }
        if (failedAttempts < 3 || failedAttempts > 10) {
            throw new IllegalArgumentException("登录失败次数必须在 3～10 次之间");
        }
        if (lockMinutes < 5 || lockMinutes > 60) {
            throw new IllegalArgumentException("锁定时长必须在 5～60 分钟之间");
        }
        if (version < 0) throw new IllegalArgumentException("策略版本不能为负数");
    }

    /** 仅在新租户开户或既有租户迁移时使用的初始值。 */
    public static TenantLoginSecurityPolicy defaults(TenantId tenantId) {
        return new TenantLoginSecurityPolicy(tenantId, 8, 5, 15, 0);
    }

    /** 失败观察窗口为固定产品值，不随三项可编辑设置变化。 */
    public int observationMinutes() { return 15; }

    /** 返回该租户新签发会话的绝对有效期。 */
    public Duration sessionDuration() { return Duration.ofHours(sessionHours); }
}
