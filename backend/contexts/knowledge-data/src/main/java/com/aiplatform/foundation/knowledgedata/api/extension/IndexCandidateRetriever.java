package com.aiplatform.foundation.knowledgedata.api.extension;

import java.util.List;

/**
 * 只从当前活动索引读取严格过滤候选的向量存储端口。
 */
public interface IndexCandidateRetriever {

    List<IndexCandidate> retrieve(IndexCandidateQuery query);
}
