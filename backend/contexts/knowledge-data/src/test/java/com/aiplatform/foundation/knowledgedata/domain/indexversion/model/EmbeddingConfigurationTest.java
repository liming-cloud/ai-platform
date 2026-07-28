package com.aiplatform.foundation.knowledgedata.domain.indexversion.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddingConfigurationTest {

    @Test
    void 受控配置绑定冻结配置与目录修订及嵌入维度() {
        var binding = EmbeddingConfiguration.governed(4001L, 3L, 3001L, 2L, 1024);

        assertTrue(binding.isGoverned());
        assertFalse(binding.isLegacyDeployment());
        assertTrue(binding.configurationId() == 4001L);
        assertTrue(binding.catalogEntryId() == 3001L);
        assertTrue(binding.dimension() == 1024);
    }
}
