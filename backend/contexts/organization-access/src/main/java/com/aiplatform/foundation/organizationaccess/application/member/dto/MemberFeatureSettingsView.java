package com.aiplatform.foundation.organizationaccess.application.member.dto;

import java.util.Map;

/** 功能编辑的无秘密投影；保存值、租户上限与当前有效值分别表达，管理员目标只读。 */
public record MemberFeatureSettingsView(String membershipId, String loginName, String status, boolean readOnly,
        Map<String, Boolean> conversationFeatures, long featureVersion,
        Map<String, Boolean> tenantAvailableFeatures, long tenantFeatureVersion, Map<String, Boolean> effectiveFeatures) { }
