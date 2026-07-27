package com.aiplatform.foundation.modelruntime.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModelCatalogIdentifierTest {

    @Test
    void 应以十进制正整数表达供应商连接标识() {
        var identifier = ProviderConnectionId.parse("310000000000000001");

        assertEquals(310000000000000001L, identifier.value());
        assertEquals("310000000000000001", identifier.toString());
        assertThrows(IllegalArgumentException.class, () -> new ProviderConnectionId(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProviderConnectionId.parse("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void 应以十进制正整数表达模型部署标识() {
        var identifier = ModelDeploymentId.parse("320000000000000001");

        assertEquals(320000000000000001L, identifier.value());
        assertEquals("320000000000000001", identifier.toString());
        assertThrows(IllegalArgumentException.class, () -> new ModelDeploymentId(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelDeploymentId.parse("00000000-0000-0000-0000-000000000002"));
    }
}
