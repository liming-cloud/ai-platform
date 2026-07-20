package com.aiplatform.knowledge.domain.port.retrieval;

import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.port.retrieval.model.RetrievalSearchQuery;
import java.util.List;

/**
 * 知识检索候选仓储端口，隔离 pgvector 和关键词检索的基础设施实现。
 */
public interface KnowledgeRetrievalCandidateRepository {
    List<RetrievalCandidate> searchVectorCandidates(RetrievalSearchQuery query);

    List<RetrievalCandidate> searchKeywordCandidates(RetrievalSearchQuery query);
}
