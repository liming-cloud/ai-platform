package com.aiplatform.foundation.knowledgedata.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import org.junit.jupiter.api.Test;

class KnowledgeIdentifierTest {

    @Test
    void 应使用十进制正整数表达知识聚合标识() {
        assertEquals(
                "210000000000000001",
                new KnowledgeSpaceId(210000000000000001L).toString());
        assertEquals(
                "220000000000000001",
                new DataSourceId(220000000000000001L).toString());
        assertEquals(
                "230000000000000001",
                new IndexVersionId(230000000000000001L).toString());
    }

    @Test
    void 应拒绝非正数和旧格式知识聚合标识() {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeSpaceId(0));
        assertThrows(IllegalArgumentException.class, () -> new DataSourceId(-1));
        assertThrows(IllegalArgumentException.class, () -> new IndexVersionId(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> KnowledgeSpaceId.parse("20000000-0000-0000-0000-000000000001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> DataSourceId.parse("30000000-0000-0000-0000-000000000001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IndexVersionId.parse("40000000-0000-0000-0000-000000000001"));
    }
}
