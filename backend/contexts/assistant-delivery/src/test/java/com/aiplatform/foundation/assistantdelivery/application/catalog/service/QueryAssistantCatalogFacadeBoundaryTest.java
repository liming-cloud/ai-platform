package com.aiplatform.foundation.assistantdelivery.application.catalog.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantCatalogService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class QueryAssistantCatalogFacadeBoundaryTest {

    @Test
    void 公开助手目录契约不得要求调用方提供领域仓储() {
        assertFalse(Arrays.stream(QueryAssistantCatalogService.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(type -> type.getName().contains(".domain.")));
    }
}
