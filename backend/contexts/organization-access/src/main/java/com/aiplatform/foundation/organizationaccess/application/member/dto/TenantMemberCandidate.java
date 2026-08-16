package com.aiplatform.foundation.organizationaccess.application.member.dto;

/** 租户成员名称选择器使用的全局账户安全摘要。 */
public record TenantMemberCandidate(
        String accountReference,
        String loginName,
        String status) {
}
