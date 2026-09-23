package com.aiplatform.foundation.trustedquery.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantCatalogService;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.trustedquery.application.candidate.service.CandidateSearchService;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryRunTerminalCommitter;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.application.diagnostic.service.QueryDiagnosticService;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunEventService;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunService;
import com.aiplatform.foundation.trustedquery.application.feedback.service.QueryFeedbackService;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.repository.QueryFeedbackRepository;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.CandidateSearchController;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryAssistantController;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryRunController;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryFeedbackController;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryDiagnosticController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TrustedQueryAutoConfigurationTest {

    private final ApplicationContextRunner coreRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            TrustedQueryCoreAutoConfiguration.class))
                    .withBean(
                            ActiveIndexLookup.class,
                            () -> mock(ActiveIndexLookup.class))
                    .withBean(
                            KnowledgeSpaceLookup.class,
                            () -> mock(KnowledgeSpaceLookup.class))
                    .withBean(
                            ModelRuntimeResolver.class,
                            () -> mock(ModelRuntimeResolver.class))
                    .withBean(
                            IndexCandidateRetriever.class,
                            () -> mock(IndexCandidateRetriever.class))
                    .withBean(
                            PublishedAssistantVersionLookup.class,
                            () -> mock(PublishedAssistantVersionLookup.class))
                    .withBean(
                            QueryRunRepository.class,
                            () -> mock(QueryRunRepository.class))
                    .withBean(
                            QueryRunTerminalCommitter.class,
                            () -> mock(QueryRunTerminalCommitter.class))
                    .withBean(
                            QueryDiagnosticFactLookup.class,
                            () -> mock(QueryDiagnosticFactLookup.class))
                    .withBean(
                            QueryFeedbackRepository.class,
                            () -> mock(QueryFeedbackRepository.class))
                    .withBean(EntityIdGenerator.class, () -> () -> 1L);

    @Test
    void 查询能力开启时应注册具体应用服务() {
        coreRunner
                .withPropertyValues("foundation.query.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CandidateSearchService.class);
                    assertThat(context).hasSingleBean(QueryRunService.class);
                    assertThat(context).hasSingleBean(QueryRunEventService.class);
                    assertThat(context).hasSingleBean(QueryFeedbackService.class);
                    assertThat(context).hasSingleBean(QueryDiagnosticService.class);
                });
    }

    @Test
    void 查询能力关闭时不得注册应用服务() {
        coreRunner
                .withPropertyValues("foundation.query.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CandidateSearchService.class);
                    assertThat(context).doesNotHaveBean(QueryRunService.class);
                    assertThat(context).doesNotHaveBean(QueryRunEventService.class);
                    assertThat(context).doesNotHaveBean(QueryFeedbackService.class);
                    assertThat(context).doesNotHaveBean(QueryDiagnosticService.class);
                });
    }

    @Test
    void 用户提供应用服务时自动配置必须退让() {
        var custom = mock(CandidateSearchService.class);
        coreRunner
                .withPropertyValues("foundation.query.enabled=true")
                .withBean(CandidateSearchService.class, () -> custom)
                .run(context -> assertThat(
                                context.getBean(CandidateSearchService.class))
                        .isSameAs(custom));
    }

    @Test
    void Web能力仅在查询角色开启时注册入口() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TrustedQueryWebAutoConfiguration.class))
                .withBean(
                        CandidateSearchService.class,
                        () -> mock(CandidateSearchService.class))
                .withBean(
                        QueryRunService.class,
                        () -> mock(QueryRunService.class))
                .withBean(
                        QueryRunEventService.class,
                        () -> mock(QueryRunEventService.class))
                .withBean(
                        QueryFeedbackService.class,
                        () -> mock(QueryFeedbackService.class))
                .withBean(
                        QueryDiagnosticService.class,
                        () -> mock(QueryDiagnosticService.class))
                .withBean(
                        QueryAssistantCatalogService.class,
                        () -> mock(QueryAssistantCatalogService.class))
                .withPropertyValues("foundation.query.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CandidateSearchController.class);
                    assertThat(context).hasSingleBean(QueryAssistantController.class);
                    assertThat(context).hasSingleBean(QueryRunController.class);
                    assertThat(context).hasSingleBean(QueryFeedbackController.class);
                    assertThat(context).hasSingleBean(QueryDiagnosticController.class);
                });
    }

    @Test
    void 缺少MyBatis会话工厂时不得注册持久化能力() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TrustedQueryPersistenceAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QueryRunRepository.class);
                    assertThat(context).doesNotHaveBean(QueryFeedbackRepository.class);
                });
    }
}
