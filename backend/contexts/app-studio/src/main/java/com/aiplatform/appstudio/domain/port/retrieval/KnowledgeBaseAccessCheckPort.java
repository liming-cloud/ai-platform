package com.aiplatform.appstudio.domain.port.retrieval;

import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;

public interface KnowledgeBaseAccessCheckPort {
    KnowledgeBaseAccessCheckResponse check(KnowledgeBaseAccessCheckRequest request);
}
