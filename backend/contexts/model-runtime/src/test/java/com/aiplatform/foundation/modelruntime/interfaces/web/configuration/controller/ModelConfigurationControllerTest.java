package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationCredentialService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request.ModelConfigurationCreateRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ModelConfigurationControllerTest {

    @Test
    void 创建响应不包含APIKey且入站秘密在调用后关闭() {
        var service = mock(ModelConfigurationCredentialService.class);
        var controller = new ModelConfigurationController(service);
        var configuration = ModelConfiguration.create(
                new ModelConfigurationId(4001L),
                ModelConfigurationScope.PLATFORM,
                null,
                null,
                "平台受控模型",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));
        when(service.create(any(), any(), any(), any(), any())).thenReturn(configuration);

        var response = controller.create(
                mock(TrustedRequestContext.class),
                new ModelConfigurationCreateRequest(
                        ModelConfigurationScope.PLATFORM,
                        "平台受控模型",
                        3001L,
                        "test-api-key"));

        var secret = ArgumentCaptor.forClass(SecretValue.class);
        verify(service).create(any(), any(), any(), any(), secret.capture());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().credentialConfigured()).isTrue();
        assertThat(response.getBody().toString()).doesNotContain("test-api-key");
        assertThatThrownBy(() -> secret.getValue().use(chars -> null))
                .isInstanceOf(IllegalStateException.class);
    }
}
