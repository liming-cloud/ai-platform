package com.aiplatform.foundation.organizationaccess.application.member.facade;

import java.util.Map;
import java.util.Set;

/** 当前请求的有效交集；普通成员空助手集合始终表示没有对象授权。 */
public record MemberEffectiveAccess(boolean tenantAdministrator, Map<String, Boolean> features, Set<Long> assistantIds) {
    public MemberEffectiveAccess { features = Map.copyOf(features); assistantIds = Set.copyOf(assistantIds); }
    public boolean allows(String feature) { return Boolean.TRUE.equals(features.get(feature)); }
}
