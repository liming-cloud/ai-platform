package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import java.time.Instant;

/** 仅用于映射失败观察行；对外不暴露计数或锁定时间。 */
public class TenantLoginAttemptEntity {
    private Instant windowStartedAt;
    private int failures;
    private Instant lockedUntil;

    public Instant windowStartedAt() { return windowStartedAt; }
    public int failures() { return failures; }
    public Instant lockedUntil() { return lockedUntil; }
    public void setWindowStartedAt(Instant value) { windowStartedAt = value; }
    public void setFailures(int value) { failures = value; }
    public void setLockedUntil(Instant value) { lockedUntil = value; }
}
