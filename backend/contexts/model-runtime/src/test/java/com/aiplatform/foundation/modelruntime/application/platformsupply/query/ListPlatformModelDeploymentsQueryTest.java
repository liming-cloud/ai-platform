package com.aiplatform.foundation.modelruntime.application.platformsupply.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import org.junit.jupiter.api.Test;

class ListPlatformModelDeploymentsQueryTest {
    @Test void 搜索规范化但不截断Unicode或解释通配符() {
        var context = org.mockito.Mockito.mock(PlatformRequestContext.class);
        var page = new PageQuery(1, 20);
        assertEquals("", new ListPlatformModelDeploymentsQuery(context, page, null).q());
        assertEquals("", new ListPlatformModelDeploymentsQuery(context, page, " \u3000 ").q());
        assertEquals("中文 %_\\", new ListPlatformModelDeploymentsQuery(context, page, "\u3000中文 %_\\  ").q());
        var boundary = "😀".repeat(64);
        assertEquals(boundary, new ListPlatformModelDeploymentsQuery(context, page, boundary).q());
        assertThrows(IllegalArgumentException.class, () -> new ListPlatformModelDeploymentsQuery(context, page, boundary + "中"));
    }
}
