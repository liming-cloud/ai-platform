package com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import org.junit.jupiter.api.Test;

class IndexCandidateQueryTest {

    @Test
    void 应接受2048维受控嵌入查询向量() {
        var query = new IndexCandidateQuery(
                new TenantId(100000000000000001L),
                210000000000000001L,
                230000000000000001L,
                EmbeddingConfiguration.governed(4001L, 1L, 3001L, 1L, 2048),
                new float[2048],
                3);

        assertEquals(2048, query.embedding().length);
    }
}
