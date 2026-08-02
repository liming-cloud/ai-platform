package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GovernedChatModelRouteTest {

    private static final GovernedChatModelBinding PRIMARY = binding(1);
    private static final GovernedChatModelBinding FALLBACK_ONE = binding(2);
    private static final GovernedChatModelBinding FALLBACK_TWO = binding(3);

    @Test
    void 应按冻结顺序保留主候选和最多两个后备候选() {
        var route = new GovernedChatModelRoute(List.of(PRIMARY, FALLBACK_ONE, FALLBACK_TWO));

        assertEquals(PRIMARY, route.primary());
        assertEquals(List.of(PRIMARY, FALLBACK_ONE, FALLBACK_TWO), route.candidates());
    }

    @Test
    void 不得接受空路由重复候选或超过两个后备候选() {
        assertThrows(IllegalArgumentException.class, () -> new GovernedChatModelRoute(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GovernedChatModelRoute(List.of(PRIMARY, PRIMARY)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GovernedChatModelRoute(List.of(
                        PRIMARY, FALLBACK_ONE, FALLBACK_TWO, binding(4))));
    }

    private static GovernedChatModelBinding binding(long value) {
        return new GovernedChatModelBinding(value, 1, value + 100, 1, 1024);
    }
}
