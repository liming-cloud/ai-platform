package com.aiplatform.foundation.organizationaccess.application.member.dto;

import java.util.Map;

/** 开户读取的当前上限和独立授权版本，不返回全局身份候选。 */
public record MemberCreateOptions(Map<String, Boolean> availableFeatures,
        long tenantFeatureVersion, long authorizationRevision) { }
