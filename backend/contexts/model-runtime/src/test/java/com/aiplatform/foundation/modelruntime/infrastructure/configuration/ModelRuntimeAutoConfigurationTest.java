package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.service.ModelDeploymentService;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationCredentialService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ControlledModelCatalogService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationAccessService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationReadService;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialUser;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelCapabilityVerificationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ControlledModelCatalogEntryMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationCredentialMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationAccessMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionRequestMapper;
import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.controller.ModelDeploymentController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ControlledModelCatalogController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationAccessController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationLifecycleController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationReadController;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller.ProviderConnectionController;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.secret.SecretResolver;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class ModelRuntimeAutoConfigurationTest {

    @Test
    void 核心能力仅在显式启用且依赖完整时注册() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeCoreAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.model-catalog.enabled=true",
                        "foundation.model-catalog.provider-connection-limit=4",
                        "foundation.model-catalog.model-deployment-limit=8")
                .withBean(
                        ProviderConnectionRepository.class,
                        () -> mock(ProviderConnectionRepository.class))
                .withBean(
                        ModelDeploymentRepository.class,
                        () -> mock(ModelDeploymentRepository.class))
                .withBean(
                        ModelDeploymentVerificationStore.class,
                        () -> mock(ModelDeploymentVerificationStore.class))
                .withBean(
                        ModelConfigurationRepository.class,
                        () -> mock(ModelConfigurationRepository.class))
                .withBean(
                        ModelConfigurationAccessRepository.class,
                        () -> mock(ModelConfigurationAccessRepository.class))
                .withBean(
                        ModelConfigurationCredentialInspector.class,
                        () -> mock(ModelConfigurationCredentialInspector.class))
                .withBean(
                        ModelConfigurationCredentialUser.class,
                        () -> mock(ModelConfigurationCredentialUser.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProbeProviderService.class);
                    assertThat(context).hasSingleBean(ProviderConnectionService.class);
                    assertThat(context).hasSingleBean(ModelDeploymentService.class);
                    assertThat(context).hasSingleBean(ModelConfigurationService.class);
                    assertThat(context).hasSingleBean(ModelConfigurationAccessService.class);
                    assertThat(context).hasSingleBean(UsableModelConfigurationLookup.class);
                    assertThat(context).hasSingleBean(GovernedEmbeddingRuntime.class);
                    assertThat(context).hasSingleBean(ChatRouteExecutor.class);
                    assertThat(context).hasSingleBean(PublishedChatDeploymentLookup.class);
                    assertThat(context).hasSingleBean(PublishedEmbeddingDeploymentLookup.class);
                });
    }

    @Test
    void 核心能力关闭或缺少仓储时不允许静默装配() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeCoreAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ProviderConnectionService.class));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeCoreAutoConfiguration.class))
                .withPropertyValues("foundation.model-catalog.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void Web能力只导入入口且要求应用服务存在() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeWebAutoConfiguration.class))
                .withPropertyValues("foundation.model-catalog.enabled=true")
                .withBean(
                        ProviderConnectionService.class,
                        () -> mock(ProviderConnectionService.class))
                .withBean(
                        ModelDeploymentService.class,
                        () -> mock(ModelDeploymentService.class))
                .withBean(
                        ModelConfigurationCredentialService.class,
                        () -> mock(ModelConfigurationCredentialService.class))
                .withBean(
                        ModelConfigurationService.class,
                        () -> mock(ModelConfigurationService.class))
                .withBean(
                        ControlledModelCatalogService.class,
                        () -> mock(ControlledModelCatalogService.class))
                .withBean(
                        ModelConfigurationAccessService.class,
                        () -> mock(ModelConfigurationAccessService.class))
                .withBean(
                        ModelConfigurationReadService.class,
                        () -> mock(ModelConfigurationReadService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderConnectionController.class);
                    assertThat(context).hasSingleBean(ModelDeploymentController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationController.class);
                    assertThat(context).hasSingleBean(ControlledModelCatalogController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationAccessController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationLifecycleController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationReadController.class);
                });
    }

    @Test
    void 运行时能力支持端口覆盖并在仓储齐全时注册解析器() {
        var secretResolver = mock(SecretResolver.class);
        var endpointPolicy = mock(OutboundEndpointPolicy.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeExecutionAutoConfiguration.class))
                .withPropertyValues("foundation.model-runtime.enabled=true")
                .withBean(
                        ProviderConnectionRepository.class,
                        () -> mock(ProviderConnectionRepository.class))
                .withBean(
                        ModelDeploymentRepository.class,
                        () -> mock(ModelDeploymentRepository.class))
                .withBean(SecretResolver.class, () -> secretResolver)
                .withBean(OutboundEndpointPolicy.class, () -> endpointPolicy)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecretResolver.class)).isSameAs(secretResolver);
                    assertThat(context.getBean(OutboundEndpointPolicy.class))
                            .isSameAs(endpointPolicy);
                    assertThat(context).hasSingleBean(ModelRuntimeResolver.class);
                });
    }

    @Test
    void 持久化能力显式启用时注册聚合仓储和验证事实存储() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimePersistenceAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.model-runtime.persistence.enabled=true")
                .withBean(
                        ProviderConnectionMapper.class,
                        () -> mock(ProviderConnectionMapper.class))
                .withBean(
                        ProviderConnectionRequestMapper.class,
                        () -> mock(ProviderConnectionRequestMapper.class))
                .withBean(
                        ModelDeploymentMapper.class,
                        () -> mock(ModelDeploymentMapper.class))
                .withBean(
                        ModelDeploymentRequestMapper.class,
                        () -> mock(ModelDeploymentRequestMapper.class))
                .withBean(
                        ModelCapabilityVerificationMapper.class,
                        () -> mock(ModelCapabilityVerificationMapper.class))
                .withBean(
                        ModelConfigurationMapper.class,
                        () -> mock(ModelConfigurationMapper.class))
                .withBean(
                        ControlledModelCatalogEntryMapper.class,
                        () -> mock(ControlledModelCatalogEntryMapper.class))
                .withBean(
                        ModelConfigurationCredentialMapper.class,
                        () -> mock(ModelConfigurationCredentialMapper.class))
                .withBean(
                        ModelConfigurationAccessMapper.class,
                        () -> mock(ModelConfigurationAccessMapper.class))
                .withBean(
                        SecretEncryptionKeyProvider.class,
                        () -> mock(SecretEncryptionKeyProvider.class))
                .withBean(
                        PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class))
                .withBean(
                        SqlSessionFactory.class,
                        () -> mock(SqlSessionFactory.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderConnectionRepository.class);
                    assertThat(context).hasSingleBean(ModelDeploymentRepository.class);
                    assertThat(context).hasSingleBean(ModelDeploymentVerificationStore.class);
                    assertThat(context).hasSingleBean(ModelConfigurationRepository.class);
                    assertThat(context).hasSingleBean(ModelConfigurationAccessRepository.class);
                    assertThat(context).hasSingleBean(ModelConfigurationCredentialWriter.class);
                    assertThat(context).hasSingleBean(ModelConfigurationCredentialInspector.class);
                });
    }

    @Test
    void 本地研发密钥提供者必须显式选择版本才装配() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeCredentialAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SecretEncryptionKeyProvider.class));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeCredentialAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.model-credentials.local-development-key-version=DEV_V1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SecretEncryptionKeyProvider.class);
                });
    }
}
