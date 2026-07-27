package com.aiplatform.foundation.shared.contract.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void 根据总数派生总页数和前后页() {
        var result = PageResult.of(
                List.of("二十一", "二十二"),
                new PageQuery(2, 20),
                42);

        assertEquals(3L, result.totalPages());
        assertTrue(result.hasPrevious());
        assertTrue(result.hasNext());
    }

    @Test
    void 空结果没有前后页且条目不可修改() {
        var result = PageResult.of(List.of(), PageQuery.defaults(), 0);

        assertFalse(result.hasPrevious());
        assertFalse(result.hasNext());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.items().add("非法条目"));
    }
}
