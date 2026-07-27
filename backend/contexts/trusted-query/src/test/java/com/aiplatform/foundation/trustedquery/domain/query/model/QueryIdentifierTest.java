package com.aiplatform.foundation.trustedquery.domain.query.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class QueryIdentifierTest {

    @Test
    void 应使用十进制正整数表达查询运行标识() {
        var identifier = QueryRunId.parse("430000000000000001");

        assertEquals(430000000000000001L, identifier.value());
        assertEquals("430000000000000001", identifier.toString());
    }

    @Test
    void 应拒绝非正数和旧格式查询运行标识() {
        assertThrows(IllegalArgumentException.class, () -> new QueryRunId(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> QueryRunId.parse("00000000-0000-0000-0000-000000000003"));
    }
}
