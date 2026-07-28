package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request;

/**
 * 为模型配置授予租户或单个成员使用权的请求。
 */
public record ModelConfigurationAccessGrantRequest(long tenantId, Long membershipId) {
}
