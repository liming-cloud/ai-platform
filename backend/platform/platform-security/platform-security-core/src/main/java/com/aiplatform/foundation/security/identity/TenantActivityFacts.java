package com.aiplatform.foundation.security.identity;

import java.util.Set;

/** 由租户事实所有者复核活动状态；不授予权限，也不返回租户内部模型。 */
@FunctionalInterface
public interface TenantActivityFacts {
    /** 在调用方短事务内按固定顺序锁定活动事实；任一目标失效则拒绝整个声明。 */
    void requireActive(Set<Long> tenantIds);
}
