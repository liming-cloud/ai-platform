package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.knowledgedata.application.datasource.service.TextDataSourceService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedDataSourceLifecycleService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedDataSourceLifecycleService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedStructuredDatasetRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.StructuredDatasetCsvSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.publication.StructuredDatasetSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceQueryService;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.GovernedFileDataSourceReadPort;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.service.IndexVersionService;
import com.aiplatform.foundation.knowledgedata.application.indexversion.service.IndexBuildWorker;
import com.aiplatform.foundation.knowledgedata.application.knowledge.service.KnowledgeSpaceService;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileDataSourceSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileSyncArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.StructuredDatasetSyncArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextContentMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextDataSourceRequestMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedFileDataSourceReadMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.ObjectStorageObjectMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.PublishedSyncArtifactMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.QualityReportMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SourceRevisionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SyncArtifactMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.StructuredDatasetFieldMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexBuildJobMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionRequestMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceRequestMapper;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller.TextDataSourceController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller.GovernedFileDataSourceController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller.GovernedStructuredDatasetController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.indexversion.controller.IndexVersionController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.knowledge.controller.KnowledgeSpaceController;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class KnowledgeDataAutoConfigurationTest {

    @Test
    void 核心能力仅在显式启用且依赖完整时注册() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataCoreAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.knowledge-data.enabled=true",
                        "foundation.knowledge-data.knowledge-space-limit=5",
                        "foundation.knowledge-data.data-source-limit=30")
                .withBean(
                        KnowledgeSpaceRepository.class,
                        () -> mock(KnowledgeSpaceRepository.class))
                .withBean(
                        TextDataSourceRepository.class,
                        () -> mock(TextDataSourceRepository.class))
                .withBean(
                        GovernedDataSourceRepository.class,
                        () -> mock(GovernedDataSourceRepository.class))
                .withBean(
                        IndexVersionRepository.class,
                        () -> mock(IndexVersionRepository.class))
                .withBean(
                        PublishedEmbeddingDeploymentLookup.class,
                        () -> mock(PublishedEmbeddingDeploymentLookup.class))
                .withBean(
                        UsableModelConfigurationLookup.class,
                        () -> mock(UsableModelConfigurationLookup.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeSpaceService.class);
                    assertThat(context).hasSingleBean(TextDataSourceService.class);
                    assertThat(context).hasSingleBean(GovernedDataSourceLifecycleService.class);
                    assertThat(context).hasSingleBean(FileDataSourceSynchronization.class);
                    assertThat(context).hasSingleBean(FileSyncArtifactPublishing.class);
                    assertThat(context).hasSingleBean(FileDataSourceSynchronizationService.class);
                    assertThat(context).hasSingleBean(FileSyncArtifactPublicationService.class);
                    assertThat(context).hasSingleBean(GovernedStructuredDatasetRegistrationService.class);
                    assertThat(context).hasSingleBean(StructuredDatasetSyncArtifactPublishing.class);
                    assertThat(context).hasSingleBean(StructuredDatasetSyncArtifactPublicationService.class);
                    assertThat(context).hasSingleBean(IndexVersionService.class);
                    assertThat(context).hasSingleBean(ActiveIndexLookup.class);
                    assertThat(context).hasSingleBean(KnowledgeSpaceLookup.class);
                    assertThat(context.getBean(KnowledgeDataProperties.class)
                            .getKnowledgeSpaceLimit()).isEqualTo(5);
                });
    }

    @Test
    void 核心能力关闭时不产生业务对象() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataCoreAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(KnowledgeSpaceService.class);
                });
    }

    @Test
    void 核心能力缺少必需仓储时启动失败() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataCoreAutoConfiguration.class))
                .withPropertyValues("foundation.knowledge-data.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 未配置受控模型查询时仍可启动数据核心但不注册索引用例() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataCoreAutoConfiguration.class))
                .withPropertyValues("foundation.knowledge-data.enabled=true")
                .withBean(
                        KnowledgeSpaceRepository.class,
                        () -> mock(KnowledgeSpaceRepository.class))
                .withBean(
                        TextDataSourceRepository.class,
                        () -> mock(TextDataSourceRepository.class))
                .withBean(
                        GovernedDataSourceRepository.class,
                        () -> mock(GovernedDataSourceRepository.class))
                .withBean(
                        IndexVersionRepository.class,
                        () -> mock(IndexVersionRepository.class))
                .withBean(
                        PublishedEmbeddingDeploymentLookup.class,
                        () -> mock(PublishedEmbeddingDeploymentLookup.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeSpaceService.class);
                    assertThat(context).doesNotHaveBean(IndexVersionService.class);
                });
    }

    @Test
    void 未配置受控模型运行时仍注册工作器并使受控索引在执行阶段失败关闭() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataJobAutoConfiguration.class))
                .withPropertyValues("foundation.ingestion.enabled=true")
                .withBean(IndexBuildQueuePort.class, () -> mock(IndexBuildQueuePort.class))
                .withBean(IndexBuildCompletionPort.class, () -> mock(IndexBuildCompletionPort.class))
                .withBean(TextContentObjectStoragePort.class, () -> mock(TextContentObjectStoragePort.class))
                .withBean(ModelRuntimeResolver.class, () -> mock(ModelRuntimeResolver.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IndexBuildWorker.class);
                });
    }

    @Test
    void 可替换对象存储端口不会被默认实现覆盖() {
        var custom = mock(TextContentObjectStoragePort.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataCoreAutoConfiguration.class))
                .withPropertyValues("foundation.knowledge-data.enabled=true")
                .withBean(
                        KnowledgeSpaceRepository.class,
                        () -> mock(KnowledgeSpaceRepository.class))
                .withBean(
                        TextDataSourceRepository.class,
                        () -> mock(TextDataSourceRepository.class))
                .withBean(
                        GovernedDataSourceRepository.class,
                        () -> mock(GovernedDataSourceRepository.class))
                .withBean(
                        IndexVersionRepository.class,
                        () -> mock(IndexVersionRepository.class))
                .withBean(
                        PublishedEmbeddingDeploymentLookup.class,
                        () -> mock(PublishedEmbeddingDeploymentLookup.class))
                .withBean(
                        UsableModelConfigurationLookup.class,
                        () -> mock(UsableModelConfigurationLookup.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .withBean(TextContentObjectStoragePort.class, () -> custom)
                .withBean(
                        TextContentStagingPort.class,
                        () -> mock(TextContentStagingPort.class))
                .run(context -> assertThat(
                        context.getBean(TextContentObjectStoragePort.class))
                        .isSameAs(custom));
    }

    @Test
    void 持久化能力支持显式启停并注册四个聚合仓储() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataPersistenceAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.knowledge-data.persistence.enabled=true")
                .withBean(
                        KnowledgeSpaceMapper.class,
                        () -> mock(KnowledgeSpaceMapper.class))
                .withBean(
                        KnowledgeSpaceRequestMapper.class,
                        () -> mock(KnowledgeSpaceRequestMapper.class))
                .withBean(
                        TextDataSourceMapper.class,
                        () -> mock(TextDataSourceMapper.class))
                .withBean(
                        TextContentMapper.class,
                        () -> mock(TextContentMapper.class))
                .withBean(
                        TextDataSourceRequestMapper.class,
                        () -> mock(TextDataSourceRequestMapper.class))
                .withBean(
                        GovernedDataSourceMapper.class,
                        () -> mock(GovernedDataSourceMapper.class))
                .withBean(
                        GovernedFileDataSourceReadMapper.class,
                        () -> mock(GovernedFileDataSourceReadMapper.class))
                .withBean(
                        SourceRevisionMapper.class,
                        () -> mock(SourceRevisionMapper.class))
                .withBean(
                        ObjectStorageObjectMapper.class,
                        () -> mock(ObjectStorageObjectMapper.class))
                .withBean(
                        StructuredDatasetFieldMapper.class,
                        () -> mock(StructuredDatasetFieldMapper.class))
                .withBean(
                        SyncArtifactMapper.class,
                        () -> mock(SyncArtifactMapper.class))
                .withBean(
                        QualityReportMapper.class,
                        () -> mock(QualityReportMapper.class))
                .withBean(
                        PublishedSyncArtifactMapper.class,
                        () -> mock(PublishedSyncArtifactMapper.class))
                .withBean(
                        IndexVersionMapper.class,
                        () -> mock(IndexVersionMapper.class))
                .withBean(
                        IndexSourceMapper.class,
                        () -> mock(IndexSourceMapper.class))
                .withBean(
                        IndexBuildJobMapper.class,
                        () -> mock(IndexBuildJobMapper.class))
                .withBean(
                        IndexVersionRequestMapper.class,
                        () -> mock(IndexVersionRequestMapper.class))
                .withBean(
                        PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class))
                .withBean(
                        SqlSessionFactory.class,
                        () -> mock(SqlSessionFactory.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeSpaceRepository.class);
                    assertThat(context).hasSingleBean(TextDataSourceRepository.class);
                    assertThat(context).hasSingleBean(GovernedDataSourceRepository.class);
                    assertThat(context).hasSingleBean(GovernedFileDataSourceReadPort.class);
                    assertThat(context).hasSingleBean(IndexVersionRepository.class);
                });
    }

    @Test
    void 查询角色仅公开知识空间和活动索引只读门面() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataQueryAutoConfiguration.class))
                .withPropertyValues("foundation.query.enabled=true")
                .withBean(
                        IndexVersionRepository.class,
                        () -> mock(IndexVersionRepository.class))
                .withBean(
                        KnowledgeSpaceRepository.class,
                        () -> mock(KnowledgeSpaceRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ActiveIndexLookup.class);
                    assertThat(context).hasSingleBean(KnowledgeSpaceLookup.class);
                    assertThat(context).doesNotHaveBean(IndexVersionService.class);
                });
    }

    @Test
    void Web能力显式导入三个入口且不创建应用服务() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataWebAutoConfiguration.class))
                .withPropertyValues("foundation.knowledge-data.enabled=true")
                .withBean(
                        KnowledgeSpaceService.class,
                        () -> mock(KnowledgeSpaceService.class))
                .withBean(
                        TextDataSourceService.class,
                        () -> mock(TextDataSourceService.class))
                .withBean(
                        GovernedFileDataSourceRegistrationService.class,
                        () -> mock(GovernedFileDataSourceRegistrationService.class))
                .withBean(
                        GovernedStructuredDatasetRegistrationService.class,
                        () -> mock(GovernedStructuredDatasetRegistrationService.class))
                .withBean(
                        StructuredDatasetCsvSynchronizationService.class,
                        () -> mock(StructuredDatasetCsvSynchronizationService.class))
                .withBean(
                        StructuredDatasetSyncArtifactPublicationService.class,
                        () -> mock(StructuredDatasetSyncArtifactPublicationService.class))
                .withBean(
                        FileDataSourceProbeService.class,
                        () -> mock(FileDataSourceProbeService.class))
                .withBean(
                        FileDataSourceSynchronizationService.class,
                        () -> mock(FileDataSourceSynchronizationService.class))
                .withBean(
                        FileSyncArtifactPublicationService.class,
                        () -> mock(FileSyncArtifactPublicationService.class))
                .withBean(
                        GovernedDataSourceLifecycleService.class,
                        () -> mock(GovernedDataSourceLifecycleService.class))
                .withBean(
                        GovernedFileDataSourceQueryService.class,
                        () -> mock(GovernedFileDataSourceQueryService.class))
                .withBean(
                        IndexVersionService.class,
                        () -> mock(IndexVersionService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeSpaceController.class);
                    assertThat(context).hasSingleBean(TextDataSourceController.class);
                    assertThat(context).hasSingleBean(GovernedFileDataSourceController.class);
                    assertThat(context).hasSingleBean(GovernedStructuredDatasetController.class);
                    assertThat(context).hasSingleBean(IndexVersionController.class);
                });
    }

    @Test
    void 未配置索引用例时Web层不注册索引入口() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataWebAutoConfiguration.class))
                .withPropertyValues("foundation.knowledge-data.enabled=true")
                .withBean(
                        KnowledgeSpaceService.class,
                        () -> mock(KnowledgeSpaceService.class))
                .withBean(
                        TextDataSourceService.class,
                        () -> mock(TextDataSourceService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeSpaceController.class);
                    assertThat(context).hasSingleBean(TextDataSourceController.class);
                    assertThat(context).doesNotHaveBean(IndexVersionController.class);
                });
    }
}
