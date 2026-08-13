package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ControlledModelCatalogService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request.ControlledModelCatalogEntryCreateFromDeploymentRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ControlledModelCatalogControllerTest {

    @Test
    void 建立目录只转发平台部署标识和修订且响应不泄露端点() {
        var service = mock(ControlledModelCatalogService.class);
        var controller = new ControlledModelCatalogController(service);
        var context = mock(PlatformRequestContext.class);
        var deploymentId = new PlatformModelDeploymentId(620000000000000001L);
        var entry = ControlledModelCatalogEntry.createFromPlatformDeployment(
                        new ControlledModelCatalogEntryId(3001L), deploymentId, 3L,
                        ProviderKind.OPENAI_COMPATIBLE, URI.create("https://example.com/v1"), "test-chat-model",
                        ModelType.CHAT, ModelRuntimeCapabilities.chat(32_768, 4_096),
                        Instant.parse("2026-08-13T00:00:00Z"))
                .publish();
        when(service.createFromDeployment(context, deploymentId, 3L)).thenReturn(entry);

        var response = controller.create(context,
                new ControlledModelCatalogEntryCreateFromDeploymentRequest(deploymentId.value(), 3L));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().toString()).doesNotContain("https://example.com/v1");
        verify(service).createFromDeployment(context, deploymentId, 3L);
    }

    @Test
    void 分页读取仅映射服务返回的已发布目录事实() {
        var service = mock(ControlledModelCatalogService.class);
        var controller = new ControlledModelCatalogController(service);
        var context = mock(TrustedRequestContext.class);
        var entry = ControlledModelCatalogEntry.reconstitute(
                new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/v1"),
                "test-chat-model",
                ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096),
                com.aiplatform.foundation.modelruntime.domain.configuration.model
                        .ControlledModelCatalogEntryStatus.PUBLISHED,
                1,
                Instant.parse("2026-07-28T00:00:00Z"));
        when(service.listPublished(context, new PageQuery(1, 20)))
                .thenReturn(PageResult.of(List.of(entry), new PageQuery(1, 20), 1));

        var response = controller.list(context, 1, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("3001");
            assertThat(item.status()).isEqualTo("PUBLISHED");
            assertThat(item.toString()).doesNotContain("api-key", "ciphertext", "nonce");
        });
    }
}
