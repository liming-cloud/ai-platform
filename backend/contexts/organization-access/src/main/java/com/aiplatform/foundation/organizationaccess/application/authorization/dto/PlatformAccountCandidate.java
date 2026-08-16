package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

/** 资源选择器使用的平台账户安全摘要。 */
public record PlatformAccountCandidate(
        String accountReference,
        String loginName,
        String status) {
}
