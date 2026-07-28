package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationReadService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationReadResult;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelConfigurationReadControllerTest {

    @Test
    void 读取响应不反显APIKey或密文材料() {
        var service = mock(ModelConfigurationReadService.class);
        var controller = new ModelConfigurationReadController(service);
        var context = mock(TrustedRequestContext.class);
        var configuration = ModelConfiguration.create(
                        new ModelConfigurationId(4001L),
                        ModelConfigurationScope.PLATFORM,
                        null,
                        null,
                        "平台受控模型",
                        new ControlledModelCatalogEntryId(3001L),
                        Instant.parse("2026-07-28T00:00:00Z"))
                .activate();
        when(service.get(context, configuration.id()))
                .thenReturn(new ModelConfigurationReadResult(configuration, true));

        var response = controller.get(context, 4001L);

        assertThat(response.credentialConfigured()).isTrue();
        assertThat(response.toString())
                .doesNotContain("api-key", "ciphertext", "nonce", "fingerprint", "masterKey");
    }
}
