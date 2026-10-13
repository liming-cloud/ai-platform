package com.aiplatform.foundation.modelruntime.application.platformsupply.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import org.junit.jupiter.api.Test;

class ListPlatformDirectoryQueriesTest {
    @Test
    void 两类目录以Unicode码点限制词组且仅剥离首尾空白() {
        var context = mock(PlatformRequestContext.class);
        var page = new PageQuery(1, 20);
        for (var value : new String[] { null, "  中文 %_\\  ", "😀".repeat(64) }) {
            var expected = value == null ? "" : value.strip();
            assertEquals(expected, new ListPlatformProviderConnectionsQuery(context, page, value).q());
            assertEquals(expected, new ListPlatformProviderModelCandidatesQuery(context, "123", 3, page, value).q());
        }
        for (var value : new String[] { "中".repeat(65), "😀".repeat(65) }) {
            assertThrows(IllegalArgumentException.class, () -> new ListPlatformProviderConnectionsQuery(context, page, value));
            assertThrows(IllegalArgumentException.class, () -> new ListPlatformProviderModelCandidatesQuery(context, "123", 3, page, value));
        }
    }
}
