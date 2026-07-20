package com.aiplatform.knowledge.application.error;

import com.aiplatform.shared.kernel.ErrorCode;

/**
 * KnowledgeOps 对外错误合同，代码值与已评审接口契约保持一致。
 */
public final class KnowledgeErrorCodes {
    public static final ErrorCode RESOURCE_NOT_VISIBLE =
            ErrorCode.of("AIP-IAM-RESOURCE-5004", "资源不可见");
    public static final ErrorCode RAG_POLICY_DENIED =
            ErrorCode.of("AIP-RAG-POLICY-5006", "RAG 授权策略拒绝");
    public static final ErrorCode RAG_INDEX_NOT_READY =
            ErrorCode.of("AIP-RAG-INDEX-5031", "知识库尚无可用索引");

    private KnowledgeErrorCodes() {
    }
}
