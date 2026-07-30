package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.DraftAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.service.AssistantService;
import com.aiplatform.foundation.assistantdelivery.application.catalog.service.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantController;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantVersionController;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
