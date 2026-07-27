package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.knowledgedata.application.datasource.service.TextDataSourceService;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.service.IndexVersionService;
import com.aiplatform.foundation.knowledgedata.application.knowledge.service.KnowledgeSpaceService;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextContentMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextDataSourceRequestMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexBuildJobMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionRequestMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceRequestMapper;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller.TextDataSourceController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.indexversion.controller.IndexVersionController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.knowledge.controller.KnowledgeSpaceController;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedEmbeddingDeploymentLookup;
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
                        IndexVersionRepository.class,
                        () -> mock(IndexVersionRepository.class))
                .withBean(
                        PublishedEmbeddingDeploymentLookup.class,
                        () -> mock(PublishedEmbeddingDeploymentLookup.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeSpaceService.class);
                    assertThat(context).hasSingleBean(TextDataSourceService.class);
                    assertThat(context).hasSingleBean(IndexVersionService.class);
                    assertThat(context).hasSingleBean(ActiveIndexLookup.class);
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
                        IndexVersionRepository.class,
                        () -> mock(IndexVersionRepository.class))
                .withBean(
                        PublishedEmbeddingDeploymentLookup.class,
                        () -> mock(PublishedEmbeddingDeploymentLookup.class))
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
    void 持久化能力支持显式启停并注册三个聚合仓储() {
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
                    assertThat(context).hasSingleBean(IndexVersionRepository.class);
                });
    }

    @Test
    void 查询角色仅公开活动索引门面() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KnowledgeDataQueryAutoConfiguration.class))
                .withPropertyValues("foundation.query.enabled=true")
                .withBean(
                        IndexVersionRepository.class,
                        () -> mock(IndexVersionRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ActiveIndexLookup.class);
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
                        IndexVersionService.class,
                        () -> mock(IndexVersionService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeSpaceController.class);
                    assertThat(context).hasSingleBean(TextDataSourceController.class);
                    assertThat(context).hasSingleBean(IndexVersionController.class);
                });
    }
}
