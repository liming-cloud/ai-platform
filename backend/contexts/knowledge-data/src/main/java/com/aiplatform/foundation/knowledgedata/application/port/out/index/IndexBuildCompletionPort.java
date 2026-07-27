package com.aiplatform.foundation.knowledgedata.application.port.out.index;

import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorBatch;
import java.time.Instant;

/**
 * 在同一事务中保存全部向量并把索引标记为就绪。
 */
public interface IndexBuildCompletionPort {

    void complete(
            IndexBuildLease lease,
            IndexVectorBatch value,
            String digest,
            Instant completedAt);
}
