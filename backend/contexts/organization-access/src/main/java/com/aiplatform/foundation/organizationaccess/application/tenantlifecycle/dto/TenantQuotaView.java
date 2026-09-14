package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

/** 已保存的租户配额展示事实；容量使用十进制字符串以避免浏览器整数精度丢失。 */
public record TenantQuotaView(int knowledgeBases, int documentsPerKnowledgeBase,
        String bytesPerKnowledgeBase, String maxFileBytes, String maxTextBytes, long version) { }
