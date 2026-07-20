package com.aiplatform.knowledge.domain.retrieval;

import java.util.List;

/**
 * RAG 质量门禁判定结果，保留所有阻断原因供代码审核和产品验收消费。
 */
public record RagQualityGateDecision(boolean blocked, List<String> findings) {
    public RagQualityGateDecision {
        findings = List.copyOf(findings);
    }
}
