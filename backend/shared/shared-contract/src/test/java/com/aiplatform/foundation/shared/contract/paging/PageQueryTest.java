package com.aiplatform.foundation.shared.contract.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void 默认分页从第一页开始且每页二十条() {
        var query = PageQuery.defaults();

        assertEquals(1L, query.pageNumber());
        assertEquals(20L, query.pageSize());
        assertEquals(0L, query.offset());
    }

    @Test
    void 拒绝超过页大小和默认最大偏移() {
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(1, 101));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(1002, 100));
    }
}
