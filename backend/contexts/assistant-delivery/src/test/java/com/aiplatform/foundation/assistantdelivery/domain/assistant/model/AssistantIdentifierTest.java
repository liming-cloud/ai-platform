package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AssistantIdentifierTest {

    @Test
    void 应使用十进制正整数表达助手聚合标识() {
        assertEquals(
                "410000000000000001",
                new AssistantId(410000000000000001L).toString());
        assertEquals(
                "420000000000000001",
                new AssistantVersionId(420000000000000001L).toString());
    }

    @Test
    void 应拒绝非正数和旧格式助手聚合标识() {
        assertThrows(IllegalArgumentException.class, () -> new AssistantId(0));
        assertThrows(IllegalArgumentException.class, () -> new AssistantVersionId(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> AssistantId.parse("00000000-0000-0000-0000-000000000001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AssistantVersionId.parse("00000000-0000-0000-0000-000000000002"));
    }
}
