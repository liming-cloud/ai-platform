package com.aiplatform.appstudio.domain.port.retrieval;

import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;

/**
 * 跨知识库上下文的检索端口，调用方必须显式传入访问主体、权限快照和检索画像。
 */
public interface KnowledgeRetrievalPort {
    KnowledgeRetrievalResponse retrieve(KnowledgeRetrievalRequest request);
}
