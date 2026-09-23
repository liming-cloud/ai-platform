package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.application.datasource.service.TextDataSourceService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedDataSourceLifecycleService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedObjectStorageDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedStructuredDatasetRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.StructuredDatasetCsvSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.publication.StructuredDatasetSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.publication.ObjectStorageSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.ObjectStorageDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.ObjectStorageSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceQueryService;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.FileSourceObjectProbe;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.GovernedFileDataSourceReadPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeSelectionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeCandidateDirectory;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.candidate.PlatformObjectRangeCandidateService;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvProjectionPort;
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
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileDataSourceSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileSyncArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.StructuredDatasetSyncArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.StructuredDatasetCsvSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.ObjectStorageSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.ObjectStorageSynchronization.ArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.UnavailableTextContentObjectStorageAdapter;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.UnavailableStructuredDatasetCsvStorageAdapter;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.TextContentFileSourceObjectProbe;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.ConfiguredPlatformObjectRangeSelectionAdapter;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.ConfiguredPlatformObjectRangeCandidateDirectory;
import com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage.UnavailablePlatformObjectContentReaderAdapter;
import com.aiplatform.foundation.knowledgedata.infrastructure.datasource.Rfc4180StructuredDatasetCsvProjectionAdapter;
import com.aiplatform.foundation.knowledgedata.infrastructure.staging.LocalTextContentStagingAdapter;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册知识空间、文本数据源和索引版本应用能力。
 */
@AutoConfiguration(after = {
    KnowledgeDataPersistenceAutoConfiguration.class
})
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
    @ConditionalOnProperty(
            name = "foundation.knowledge-data.object-storage.enabled",
            havingValue = "false",
            matchIfMissing = true)
    StructuredDatasetCsvStoragePort unavailableStructuredDatasetCsvStorage() {
        return new UnavailableStructuredDatasetCsvStorageAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "foundation.knowledge-data.object-storage.enabled",
            havingValue = "false",
            matchIfMissing = true)
    PlatformObjectContentReaderPort unavailablePlatformObjectContentReader() {
        return new UnavailablePlatformObjectContentReaderAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformObjectRangeSelectionPort platformObjectRangeSelectionPort(KnowledgeDataProperties properties) {
        return new ConfiguredPlatformObjectRangeSelectionAdapter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformObjectRangeCandidateDirectory platformObjectRangeCandidateDirectory(KnowledgeDataProperties properties) {
        return new ConfiguredPlatformObjectRangeCandidateDirectory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformObjectRangeCandidateService platformObjectRangeCandidateService(
            PlatformObjectRangeCandidateDirectory directory) {
        return new PlatformObjectRangeCandidateService(directory);
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
    @ConditionalOnBean(GovernedFileDataSourceReadPort.class)
    GovernedFileDataSourceQueryService governedFileDataSourceQueryService(
            GovernedFileDataSourceReadPort reads) {
        return new GovernedFileDataSourceQueryService(reads);
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
    FileDataSourceSynchronization fileDataSourceSynchronization() {
        return new FileDataSourceSynchronization();
    }

    @Bean
    @ConditionalOnMissingBean
    FileSyncArtifactPublishing fileSyncArtifactPublishing() {
        return new FileSyncArtifactPublishing();
    }

    @Bean
    @ConditionalOnMissingBean
    FileDataSourceSynchronizationService fileDataSourceSynchronizationService(
            GovernedDataSourceRepository repository,
            TextContentObjectStoragePort storage,
            EntityIdGenerator identifiers,
            FileDataSourceSynchronization synchronization) {
        return new FileDataSourceSynchronizationService(
                repository, storage, Clock.systemUTC(), identifiers, synchronization);
    }

    @Bean
    @ConditionalOnMissingBean
    FileSyncArtifactPublicationService fileSyncArtifactPublicationService(
            GovernedDataSourceRepository repository, FileSyncArtifactPublishing publishing) {
        return new FileSyncArtifactPublicationService(repository, Clock.systemUTC(), publishing);
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
    GovernedStructuredDatasetRegistrationService governedStructuredDatasetRegistrationService(
            GovernedDataSourceRepository repository,
            TextContentStagingPort staging,
            StructuredDatasetCsvStoragePort storage,
            EntityIdGenerator identifiers) {
        return new GovernedStructuredDatasetRegistrationService(
                repository, staging, storage, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    StructuredDatasetCsvProjectionPort structuredDatasetCsvProjectionPort(
            StructuredDatasetCsvStoragePort storage, KnowledgeDataProperties properties) {
        return new Rfc4180StructuredDatasetCsvProjectionAdapter(
                storage,
                properties.getMaximumTextBytes(),
                properties.getMaximumStructuredDatasetCsvRows(),
                properties.getMaximumStructuredDatasetCsvLineBytes(),
                properties.getMaximumStructuredDatasetCsvFieldCharacters());
    }

    @Bean
    @ConditionalOnMissingBean
    StructuredDatasetCsvSynchronization structuredDatasetCsvSynchronization() {
        return new StructuredDatasetCsvSynchronization();
    }

    @Bean
    @ConditionalOnMissingBean
    StructuredDatasetCsvSynchronizationService structuredDatasetCsvSynchronizationService(
            GovernedDataSourceRepository repository,
            StructuredDatasetCsvProjectionPort projection,
            TextContentStagingPort staging,
            TextContentObjectStoragePort storage,
            EntityIdGenerator identifiers,
            StructuredDatasetCsvSynchronization synchronization) {
        return new StructuredDatasetCsvSynchronizationService(
                repository, projection, staging, storage, Clock.systemUTC(), identifiers, synchronization);
    }

    @Bean
    @ConditionalOnMissingBean
    StructuredDatasetSyncArtifactPublishing structuredDatasetSyncArtifactPublishing() {
        return new StructuredDatasetSyncArtifactPublishing();
    }

    @Bean
    @ConditionalOnMissingBean
    StructuredDatasetSyncArtifactPublicationService structuredDatasetSyncArtifactPublicationService(
            GovernedDataSourceRepository repository, StructuredDatasetSyncArtifactPublishing publishing) {
        return new StructuredDatasetSyncArtifactPublicationService(repository, Clock.systemUTC(), publishing);
    }

    @Bean
    @ConditionalOnMissingBean
    ArtifactPublishing objectStorageSyncArtifactPublishing() {
        return new ArtifactPublishing();
    }

    @Bean
    @ConditionalOnMissingBean
    ObjectStorageSyncArtifactPublicationService objectStorageSyncArtifactPublicationService(
            GovernedDataSourceRepository repository,
            ArtifactPublishing publishing) {
        return new ObjectStorageSyncArtifactPublicationService(repository, Clock.systemUTC(), publishing);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PlatformObjectRangeSelectionPort.class)
    GovernedObjectStorageDataSourceRegistrationService governedObjectStorageDataSourceRegistrationService(
            GovernedDataSourceRepository repository,
            PlatformObjectRangeSelectionPort selections,
            EntityIdGenerator identifiers) {
        return new GovernedObjectStorageDataSourceRegistrationService(
                repository, selections, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    ObjectStorageDataSourceProbeService objectStorageDataSourceProbeService(
            GovernedDataSourceRepository repository, PlatformObjectRangeSelectionPort selections) {
        return new ObjectStorageDataSourceProbeService(repository, selections);
    }

    @Bean
    @ConditionalOnMissingBean
    ObjectStorageSynchronization objectStorageSynchronization() {
        return new ObjectStorageSynchronization();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PlatformObjectContentReaderPort.class)
    ObjectStorageSynchronizationService objectStorageSynchronizationService(
            GovernedDataSourceRepository repository,
            PlatformObjectContentReaderPort reader,
            TextContentStagingPort staging,
            TextContentObjectStoragePort storage,
            EntityIdGenerator identifiers,
            ObjectStorageSynchronization synchronization) {
        return new ObjectStorageSynchronizationService(
                repository, reader, staging, storage, Clock.systemUTC(), identifiers, synchronization);
    }

    @Bean
    @ConditionalOnBean(UsableModelConfigurationLookup.class)
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
