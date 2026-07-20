package com.aiplatform.knowledge.domain.port.answer;

import com.aiplatform.knowledge.domain.port.answer.model.KnowledgeAnswerEvidence;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;

/**
 * 知识答案生成端口，要求实现方只能基于已授权的检索证据生成回答。
 */
public interface KnowledgeAnswerGenerationPort {
    String generateAnswer(
            TenantId tenantId,
            KnowledgeModelRuntimeDescriptor runtimeDescriptor,
            String query,
            List<KnowledgeAnswerEvidence> evidence
    );
}
