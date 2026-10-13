package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.service.ModelDeploymentService;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ProviderModelDiscoveryLookup;
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
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionPublicationService;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryHistoryReader;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryReadService;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelCapabilityVerificationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ControlledModelCatalogEntryMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationCredentialMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationAccessMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionCredentialMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionRequestMapper;
import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.controller.ModelDeploymentController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ControlledModelCatalogController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationAccessController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationLifecycleController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationReadController;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller.ProviderConnectionController;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller.PlatformProviderConnectionController;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller.PlatformProviderConnectionPublicationController;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller.PlatformProviderModelDiscoveryController;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.secret.SecretResolver;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPublicationRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionPublicationMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelRuntimeAutoConfigurationTest {

    @Test
    void 核心能力仅在显式启用且依赖完整时注册() {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeCoreAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.model-catalog.enabled=true",
                        "foundation.model-catalog.provider-connection-limit=4",
                        "foundation.model-catalog.model-deployment-limit=8")
                .withBean(OutboundEndpointPolicy.class, () -> mock(OutboundEndpointPolicy.class))
                .withBean(
                        ProviderConnectionRepository.class,
                        () -> mock(ProviderConnectionRepository.class))
                .withBean(
                        PlatformProviderConnectionRepository.class,
                        () -> mock(PlatformProviderConnectionRepository.class))
                .withBean(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts.class,
                        () -> mock(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts.class))
                .withBean(
                        PlatformProviderConnectionCredentialWriter.class,
                        () -> mock(PlatformProviderConnectionCredentialWriter.class))
                .withBean(
                        PlatformProviderConnectionCredentialTransaction.class,
                        () -> mock(PlatformProviderConnectionCredentialTransaction.class))
                .withBean(
                        PlatformProviderModelDiscoveryStore.class,
                        () -> mock(PlatformProviderModelDiscoveryStore.class))
                .withBean(
                        ModelDeploymentRepository.class,
                        () -> mock(ModelDeploymentRepository.class))
                .withBean(
                        ModelDeploymentVerificationStore.class,
                        () -> mock(ModelDeploymentVerificationStore.class))
                .withBean(
                        ProviderModelDiscoveryStore.class,
                        () -> mock(ProviderModelDiscoveryStore.class))
                .withBean(
                        ProviderModelDiscoveryHistoryReader.class,
                        () -> mock(ProviderModelDiscoveryHistoryReader.class))
                .withBean(
                        ProviderModelDiscoveryLookup.class,
                        () -> mock(ProviderModelDiscoveryLookup.class))
                .withBean(
                        ProviderConnectionCredentialWriter.class,
                        () -> mock(ProviderConnectionCredentialWriter.class))
                .withBean(
                        ProviderConnectionCredentialTransaction.class,
                        () -> mock(ProviderConnectionCredentialTransaction.class))
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
                .withBean(EntityIdGenerator.class, () -> () -> 1001L);

        runner.withBean(PlatformProviderConnectionPublicationRepository.class,
                        () -> mock(PlatformProviderConnectionPublicationRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProbeProviderService.class);
                    assertThat(context).hasSingleBean(ProviderConnectionService.class);
                    assertThat(context).hasSingleBean(ProviderModelDiscoveryService.class);
                    assertThat(context).hasSingleBean(ProviderModelDiscoveryReadService.class);
                    assertThat(context).hasSingleBean(PlatformProviderModelDiscoveryService.class);
                    assertThat(context).hasSingleBean(PlatformProviderConnectionService.class);
                    assertThat(context).hasSingleBean(PlatformProviderConnectionPublicationService.class);
                    assertThat(context).hasSingleBean(ModelDeploymentService.class);
                    assertThat(context).hasSingleBean(ModelConfigurationService.class);
                    assertThat(context).hasSingleBean(ModelConfigurationAccessService.class);
                    assertThat(context).hasSingleBean(UsableModelConfigurationLookup.class);
                    assertThat(context).hasSingleBean(GovernedEmbeddingRuntime.class);
                    assertThat(context).hasSingleBean(ChatRouteExecutor.class);
                    assertThat(context).hasSingleBean(PublishedChatDeploymentLookup.class);
                    assertThat(context).hasSingleBean(PublishedEmbeddingDeploymentLookup.class);
                });

        // 已允许连接写入时，缺少发布持久化不能静默退化为部分可写服务。
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(
                    org.springframework.beans.factory.NoSuchBeanDefinitionException.class)
                    .hasStackTraceContaining(PlatformProviderConnectionPublicationRepository.class.getName());
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
                .withPropertyValues(
                        "foundation.model-catalog.enabled=true",
                        "foundation.model-catalog.web.enabled=true")
                .withBean(
                        ProviderConnectionService.class,
                        () -> mock(ProviderConnectionService.class))
                .withBean(
                        ProviderModelDiscoveryService.class,
                        () -> mock(ProviderModelDiscoveryService.class))
                .withBean(
                        ProviderModelDiscoveryReadService.class,
                        () -> mock(ProviderModelDiscoveryReadService.class))
                .withBean(
                        PlatformProviderConnectionService.class,
                        () -> mock(PlatformProviderConnectionService.class))
                .withBean(
                        PlatformProviderConnectionPublicationService.class,
                        () -> mock(PlatformProviderConnectionPublicationService.class))
                .withBean(
                        PlatformProviderModelDiscoveryService.class,
                        () -> mock(PlatformProviderModelDiscoveryService.class))
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
                    assertThat(context).hasSingleBean(PlatformProviderConnectionController.class);
                    assertThat(context).hasSingleBean(PlatformProviderConnectionPublicationController.class);
                    assertThat(context).hasSingleBean(PlatformProviderModelDiscoveryController.class);
                    assertThat(context).hasSingleBean(ModelDeploymentController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationController.class);
                    assertThat(context).hasSingleBean(ControlledModelCatalogController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationAccessController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationLifecycleController.class);
                    assertThat(context).hasSingleBean(ModelConfigurationReadController.class);
                });
    }

    @Test
    void 未启用控制平面Web时不得导入模型目录接口() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ModelRuntimeWebAutoConfiguration.class))
                .withPropertyValues("foundation.model-catalog.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ModelRuntimeWebAutoConfiguration.class));
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
                .withBean(PlatformProviderConnectionPublicationMapper.class,
                        () -> mock(PlatformProviderConnectionPublicationMapper.class))
                .withBean(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper.class,
                        () -> mock(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper.class))
                .withBean(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper.class,
                        () -> mock(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper.class))
                .withBean(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper.class,
                        () -> mock(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper.class))
                .withBean(
                        ProviderConnectionMapper.class,
                        () -> mock(ProviderConnectionMapper.class))
                .withBean(
                        ProviderConnectionRequestMapper.class,
                        () -> mock(ProviderConnectionRequestMapper.class))
                .withBean(
                        ProviderConnectionCredentialMapper.class,
                        () -> mock(ProviderConnectionCredentialMapper.class))
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
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(
                        SqlSessionFactory.class,
                        () -> mock(SqlSessionFactory.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderConnectionRepository.class);
                    assertThat(context).hasSingleBean(ModelDeploymentRepository.class);
                    assertThat(context).hasSingleBean(ModelDeploymentVerificationStore.class);
                    assertThat(context).hasSingleBean(ProviderModelDiscoveryStore.class);
                    assertThat(context).hasSingleBean(ProviderModelDiscoveryHistoryReader.class);
                    assertThat(context).hasSingleBean(ProviderModelDiscoveryLookup.class);
                    assertThat(context).hasSingleBean(PlatformProviderConnectionRepository.class);
                    assertThat(context).hasSingleBean(PlatformProviderConnectionPublicationRepository.class);
                    assertThat(context).hasSingleBean(PlatformProviderModelDiscoveryStore.class);
                    assertThat(context).hasSingleBean(ModelConfigurationRepository.class);
                    assertThat(context).hasSingleBean(ModelConfigurationAccessRepository.class);
                    assertThat(context).hasSingleBean(ProviderConnectionCredentialWriter.class);
                    assertThat(context).hasSingleBean(ProviderConnectionCredentialTransaction.class);
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
