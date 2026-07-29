package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.application.datasource.service.TextDataSourceService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedDataSourceLifecycleService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.FileSourceObjectProbe;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.service.ActiveIndexLookupService;
import com.aiplatform.foundation.knowledgedata.application.indexversion.service.IndexVersionService;
import com.aiplatform.foundation.knowledgedata.application.knowledge.service.KnowledgeSpaceService;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup;
import com.aiplatform.foundation.knowledgedata.application.knowledge.service.KnowledgeSpaceLookupService;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.UnavailableTextContentObjectStorageAdapter;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.TextContentFileSourceObjectProbe;
import com.aiplatform.foundation.knowledgedata.infrastructure.staging.LocalTextContentStagingAdapter;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册知识空间、文本数据源和索引版本应用能力。
 */
@AutoConfiguration(after = KnowledgeDataPersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.knowledge-data.enabled",
        havingValue = "true")
@EnableConfigurationProperties(KnowledgeDataProperties.class)
public class KnowledgeDataCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    KnowledgeSpaceService knowledgeSpaceService(
            KnowledgeSpaceRepository repository,
            EntityIdGenerator identifiers,
            KnowledgeDataProperties properties) {
        return new KnowledgeSpaceService(
                repository,
                Clock.systemUTC(),
                identifiers,
                properties.getKnowledgeSpaceLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeSpaceLookup knowledgeSpaceLookup(KnowledgeSpaceRepository repository) {
        return new KnowledgeSpaceLookupService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    TextContentStagingPort textContentStagingPort(
            KnowledgeDataProperties properties) {
        return new LocalTextContentStagingAdapter(
                properties.getStagingDirectory(),
                properties.getMaximumTextBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "foundation.knowledge-data.object-storage.enabled",
            havingValue = "false",
            matchIfMissing = true)
    TextContentObjectStoragePort unavailableTextContentObjectStorage() {
        return new UnavailableTextContentObjectStorageAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    TextDataSourceService textDataSourceService(
            TextDataSourceRepository repository,
            TextContentStagingPort staging,
            TextContentObjectStoragePort objectStorage,
            EntityIdGenerator identifiers,
            KnowledgeDataProperties properties) {
        return new TextDataSourceService(
                repository,
                staging,
                objectStorage,
                Clock.systemUTC(),
                identifiers,
                properties.getDataSourceLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    GovernedDataSourceLifecycleService governedDataSourceLifecycleService(
            GovernedDataSourceRepository repository) {
        return new GovernedDataSourceLifecycleService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    FileSourceObjectProbe fileSourceObjectProbe(TextContentObjectStoragePort storage) {
        return new TextContentFileSourceObjectProbe(storage);
    }

    @Bean
    @ConditionalOnMissingBean
    FileDataSourceProbeService fileDataSourceProbeService(
            GovernedDataSourceRepository repository,
            FileSourceObjectProbe probe) {
        return new FileDataSourceProbeService(repository, probe);
    }

    @Bean
    @ConditionalOnMissingBean
    GovernedFileDataSourceRegistrationService governedFileDataSourceRegistrationService(
            GovernedDataSourceRepository repository,
            TextContentStagingPort staging,
            TextContentObjectStoragePort storage,
            EntityIdGenerator identifiers) {
        return new GovernedFileDataSourceRegistrationService(
                repository, staging, storage, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    IndexVersionService indexVersionService(
            IndexVersionRepository repository,
            UsableModelConfigurationLookup embeddingConfigurations,
            EntityIdGenerator identifiers) {
        return new IndexVersionService(
                repository,
                embeddingConfigurations,
                Clock.systemUTC(),
                identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    ActiveIndexLookup activeIndexLookup(IndexVersionRepository repository) {
        return new ActiveIndexLookupService(repository);
    }
}
