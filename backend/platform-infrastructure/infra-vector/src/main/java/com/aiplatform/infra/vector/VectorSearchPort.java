package com.aiplatform.infra.vector;

import com.aiplatform.infra.vector.model.VectorSearchResult;
import java.util.List;

/**
 * 向量检索基础设施端口，隔离业务上下文与具体向量库实现。
 */
public interface VectorSearchPort {
    List<VectorSearchResult> search(String tenantId, String knowledgeBaseId, float[] embedding, int topK);
}
