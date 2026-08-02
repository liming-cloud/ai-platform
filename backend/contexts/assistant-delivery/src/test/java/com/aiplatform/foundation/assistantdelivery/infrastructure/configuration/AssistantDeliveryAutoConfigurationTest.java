package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.DraftAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.service.AssistantService;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutor;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.service.AssistantTestRunService;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.port.out.AssistantTestRunTerminalCommitter;
import com.aiplatform.foundation.assistantdelivery.application.catalog.service.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository.AssistantTestRunRepository;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestCitationMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestRunMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestRunModelInvocationTraceMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantRequestMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantVersionMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantVersionChatRouteCandidateMapper;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantController;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantTestRunController;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantVersionController;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.apache.ibatis.session.SqlSessionFactory;

class AssistantDeliveryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AssistantDeliveryCoreAutoConfiguration.class,
                    AssistantDeliveryManagementAutoConfiguration.class,
                    AssistantDeliveryWebAutoConfiguration.class))
            .withBean(AssistantRepository.class, () -> mock(AssistantRepository.class))
            .withBean(
                    PublishedChatDeploymentLookup.class,
                    () -> mock(PublishedChatDeploymentLookup.class))
            .withBean(ActiveIndexLookup.class, () -> mock(ActiveIndexLookup.class))
            .withBean(EntityIdGenerator.class, () -> () -> 1L);

    private final ApplicationContextRunner persistenceRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AssistantDeliveryPersistenceAutoConfiguration.class))
            .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
            .withBean(AssistantMapper.class, () -> mock(AssistantMapper.class))
            .withBean(AssistantRequestMapper.class, () -> mock(AssistantRequestMapper.class))
            .withBean(AssistantVersionMapper.class, () -> mock(AssistantVersionMapper.class))
            .withBean(
                    AssistantVersionChatRouteCandidateMapper.class,
                    () -> mock(AssistantVersionChatRouteCandidateMapper.class))
            .withBean(AssistantTestRunMapper.class, () -> mock(AssistantTestRunMapper.class))
            .withBean(
                    AssistantTestRunModelInvocationTraceMapper.class,
                    () -> mock(AssistantTestRunModelInvocationTraceMapper.class))
            .withBean(AssistantTestCitationMapper.class, () -> mock(AssistantTestCitationMapper.class))
            .withBean(EntityIdGenerator.class, () -> () -> 1L)
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

    @Test
    void 能力开启时应注册应用门面和控制面入口() {
        runner.withPropertyValues(
                        "foundation.assistant-delivery.enabled=true",
                        "foundation.control-plane.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AssistantService.class);
                    assertThat(context).hasSingleBean(PublishedAssistantVersionLookup.class);
                    assertThat(context).hasSingleBean(DraftAssistantVersionLookup.class);
                    assertThat(context).hasSingleBean(QueryAssistantCatalogService.class);
                    assertThat(context).hasSingleBean(AssistantController.class);
                    assertThat(context).hasSingleBean(AssistantVersionController.class);
                });
    }

    @Test
    void 能力关闭时不得注册应用服务() {
        runner.withPropertyValues(
                        "foundation.assistant-delivery.enabled=false",
                        "foundation.control-plane.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AssistantService.class);
                    assertThat(context)
                            .doesNotHaveBean(PublishedAssistantVersionLookup.class);
                    assertThat(context)
                            .doesNotHaveBean(DraftAssistantVersionLookup.class);
                    assertThat(context)
                            .doesNotHaveBean(QueryAssistantCatalogService.class);
                    assertThat(context).doesNotHaveBean(AssistantController.class);
                });
    }

    @Test
    void 用户提供实现时自动配置必须退让() {
        var custom = mock(PublishedAssistantVersionLookup.class);
        runner.withPropertyValues("foundation.assistant-delivery.enabled=true")
                .withBean(PublishedAssistantVersionLookup.class, () -> custom)
                .run(context -> assertThat(
                                context.getBean(PublishedAssistantVersionLookup.class))
                        .isSameAs(custom));
    }

    @Test
    void 持久化开启时应注册独立草稿测试运行仓储() {
        persistenceRunner.withPropertyValues("foundation.assistant-delivery.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AssistantTestRunRepository.class);
                    assertThat(context).hasSingleBean(AssistantTestRunTerminalCommitter.class);
                });
    }

    @Test
    void 可信执行依赖齐备时应装配草稿测试服务和HTTP入口() {
        runner.withPropertyValues(
                        "foundation.assistant-delivery.enabled=true",
                        "foundation.control-plane.enabled=true")
                .withBean(AssistantTestRunRepository.class, () -> mock(AssistantTestRunRepository.class))
                .withBean(
                        AssistantTestRunTerminalCommitter.class,
                        () -> mock(AssistantTestRunTerminalCommitter.class))
                .withBean(ModelRuntimeResolver.class, () -> mock(ModelRuntimeResolver.class))
                .withBean(UsableModelConfigurationLookup.class, () -> mock(UsableModelConfigurationLookup.class))
                .withBean(GovernedEmbeddingRuntime.class, () -> mock(GovernedEmbeddingRuntime.class))
                .withBean(GovernedChatRuntime.class, () -> mock(GovernedChatRuntime.class))
                .withBean(ChatRouteExecutor.class, () -> mock(ChatRouteExecutor.class))
                .withBean(IndexCandidateRetriever.class, () -> mock(IndexCandidateRetriever.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AssistantTrustedAnswerExecutor.class);
                    assertThat(context).hasSingleBean(AssistantTestRunService.class);
                    assertThat(context).hasSingleBean(AssistantTestRunController.class);
                });
    }
}
