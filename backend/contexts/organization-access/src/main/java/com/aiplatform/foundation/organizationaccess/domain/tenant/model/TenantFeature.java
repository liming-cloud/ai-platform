package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import java.util.Arrays;

/** 已验收的租户功能目录；代码稳定，不包含不可关闭的安全边界。 */
public enum TenantFeature {
    KNOWLEDGE_MANAGEMENT("knowledgeManagement"),
    MODEL_GOVERNANCE("modelGovernance"),
    ASSISTANT_DELIVERY("assistantDelivery"),
    EVALUATION_GOVERNANCE("evaluationGovernance"),
    ASSISTANT_SELECTION("assistantSelection"),
    CONVERSATION("conversation"),
    OWN_HISTORY("ownHistory"),
    AUTHORIZED_CITATIONS("authorizedCitations");

    private final String code;

    TenantFeature(String code) { this.code = code; }

    public String code() { return code; }

    /** 未知功能代码必须拒绝，不能推断为可关闭的安全控制。 */
    public static TenantFeature fromCode(String code) {
        return Arrays.stream(values()).filter(feature -> feature.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知租户功能代码"));
    }
}
