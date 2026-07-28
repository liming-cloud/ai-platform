package com.aiplatform.foundation.modelruntime.application.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationPage;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelConfigurationReadServiceTest {

    @Test
    void 当前可使用的成员只能读取脱敏配置元数据() {
        var configurations = mock(ModelConfigurationRepository.class);
        var management = mock(ModelConfigurationService.class);
        var access = mock(ModelConfigurationAccessService.class);
        var credentials = mock(ModelConfigurationCredentialInspector.class);
        var service = new ModelConfigurationReadService(configurations, management, access, credentials);
        var context = mock(TrustedRequestContext.class);
        var configuration = ModelConfiguration.create(
                        new ModelConfigurationId(4001L),
                        ModelConfigurationScope.TENANT,
                        new com.aiplatform.foundation.shared.kernel.tenant.TenantId(2001L),
                        null,
                        "租户受控模型",
                        new ControlledModelCatalogEntryId(3001L),
                        Instant.parse("2026-07-28T00:00:00Z"))
                .activate();

        when(configurations.findConfiguration(configuration.id())).thenReturn(Optional.of(configuration));
        when(management.mayManage(context, configuration)).thenReturn(false);
        when(access.mayUse(context, configuration)).thenReturn(true);
        when(credentials.isConfigured(configuration.id())).thenReturn(true);

        ModelConfigurationReadResult result = service.get(context, configuration.id());

        assertThat(result.configuration()).isEqualTo(configuration);
        assertThat(result.credentialConfigured()).isTrue();
    }

    @Test
    void 无管理权且无使用权时统一按不存在处理() {
        var configurations = mock(ModelConfigurationRepository.class);
        var management = mock(ModelConfigurationService.class);
        var access = mock(ModelConfigurationAccessService.class);
        var credentials = mock(ModelConfigurationCredentialInspector.class);
        var service = new ModelConfigurationReadService(configurations, management, access, credentials);
        var context = mock(TrustedRequestContext.class);
        var configuration = ModelConfiguration.create(
                new ModelConfigurationId(4001L),
                ModelConfigurationScope.PLATFORM,
                null,
                null,
                "平台受控模型",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));

        when(configurations.findConfiguration(configuration.id())).thenReturn(Optional.of(configuration));
        when(management.mayManage(context, configuration)).thenReturn(false);
        when(access.mayUse(context, configuration)).thenReturn(false);

        assertThatThrownBy(() -> service.get(context, configuration.id()))
                .isInstanceOf(ModelConfigurationNotFoundException.class);
        verifyNoInteractions(credentials);
    }

    @Test
    void 管理者可读取尚未启用的配置但不读取凭据内容() {
        var configurations = mock(ModelConfigurationRepository.class);
        var management = mock(ModelConfigurationService.class);
        var access = mock(ModelConfigurationAccessService.class);
        var credentials = mock(ModelConfigurationCredentialInspector.class);
        var service = new ModelConfigurationReadService(configurations, management, access, credentials);
        var context = mock(TrustedRequestContext.class);
        var configuration = ModelConfiguration.create(
                new ModelConfigurationId(4001L),
                ModelConfigurationScope.PLATFORM,
                null,
                null,
                "平台受控模型",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));

        when(configurations.findConfiguration(configuration.id())).thenReturn(Optional.of(configuration));
        when(management.mayManage(context, configuration)).thenReturn(true);
        when(credentials.isConfigured(configuration.id())).thenReturn(false);

        var result = service.get(context, configuration.id());

        assertThat(result.credentialConfigured()).isFalse();
        verifyNoInteractions(access);
    }

    @Test
    void 分页列表只投影当前成员可见的安全模型配置事实() {
        var configurations = mock(ModelConfigurationRepository.class);
        var management = mock(ModelConfigurationService.class);
        var access = mock(ModelConfigurationAccessService.class);
        var credentials = mock(ModelConfigurationCredentialInspector.class);
        var service = new ModelConfigurationReadService(configurations, management, access, credentials);
        var context = mock(TrustedRequestContext.class);
        var tenantId = new com.aiplatform.foundation.shared.kernel.tenant.TenantId(2001L);
        var membershipId = new MembershipId(2101L);
        var configuration = ModelConfiguration.create(
                        new ModelConfigurationId(4001L),
                        ModelConfigurationScope.TENANT,
                        tenantId,
                        null,
                        "租户嵌入模型",
                        new ControlledModelCatalogEntryId(3001L),
                        Instant.parse("2026-07-28T00:00:00Z"))
                .activate();
        var entry = ControlledModelCatalogEntry.create(
                        configuration.catalogEntryId(),
                        ProviderKind.OPENAI_COMPATIBLE,
                        java.net.URI.create("https://example.test/v1"),
                        "embedding-test",
                        ModelType.EMBEDDING,
                        new ModelRuntimeCapabilities(8192, null, 128),
                        Instant.parse("2026-07-28T00:00:00Z"))
                .publish();

        when(context.tenantId()).thenReturn(tenantId);
        when(context.membershipId()).thenReturn(membershipId);
        when(configurations.findVisiblePage(
                tenantId,
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(
                        membershipId.value()),
                1,
                20)).thenReturn(new ModelConfigurationPage(List.of(configuration), 1, 20, 1));
        when(configurations.findCatalogEntry(configuration.catalogEntryId())).thenReturn(Optional.of(entry));
        when(credentials.isConfigured(configuration.id())).thenReturn(true);

        var page = service.list(context, PageQuery.defaults());

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.modelType()).isEqualTo(ModelType.EMBEDDING);
            assertThat(item.credentialConfigured()).isTrue();
            assertThat(item.toString()).doesNotContain("api-key", "ciphertext", "nonce", "fingerprint");
        });
    }
}
