package com.aiplatform.foundation.knowledgedata.application.indexversion.facade;

import java.util.List;

/** 向其他上下文公开仅在授权索引内检索候选的能力。 */
public interface IndexCandidateRetriever {
    /** 按租户、知识空间和冻结嵌入配置检索候选。 */
    List<IndexCandidate> retrieve(IndexCandidateQuery query);
}
