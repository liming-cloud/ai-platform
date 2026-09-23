package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectReadRequest;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.CsvObjectReference;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.CsvObjectStorageExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.CsvObjectWriteRequest;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStorageRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextObjectReference;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextObjectStorageExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextObjectWriteRequest;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 在知识上下文内把公开存储扩展契约转换成领域端口。 */
@AutoConfiguration(
        afterName = {
                "com.aiplatform.foundation.objectstorage.minio.autoconfigure.MinioKnowledgeDataAutoConfiguration",
                "com.aiplatform.foundation.objectstorage.minio.autoconfigure.MinioIngestionAutoConfiguration"
        },
        before = {KnowledgeDataCoreAutoConfiguration.class, KnowledgeDataJobAutoConfiguration.class})
public class KnowledgeDataStorageExtensionAutoConfiguration {

    @Bean
    @ConditionalOnBean(TextObjectStorageExtensionPort.class)
    @ConditionalOnMissingBean(TextContentObjectStoragePort.class)
    TextContentObjectStoragePort textContentStorageBridge(TextObjectStorageExtensionPort extension) {
        return new TextStorageBridge(extension);
    }

    @Bean
    @ConditionalOnBean(CsvObjectStorageExtensionPort.class)
    @ConditionalOnMissingBean(StructuredDatasetCsvStoragePort.class)
    StructuredDatasetCsvStoragePort csvStorageBridge(CsvObjectStorageExtensionPort extension) {
        return new CsvStorageBridge(extension);
    }

    @Bean
    @ConditionalOnBean(PlatformObjectContentReaderExtensionPort.class)
    @ConditionalOnMissingBean(PlatformObjectContentReaderPort.class)
    PlatformObjectContentReaderPort platformObjectReaderBridge(PlatformObjectContentReaderExtensionPort extension) {
        return new PlatformReaderBridge(extension);
    }

    private record TextStorageBridge(TextObjectStorageExtensionPort extension)
            implements TextContentObjectStoragePort {
        @Override
        public TextContentReference put(TextContentStorageRequest request) {
            var value = extension.put(new TextObjectWriteRequest(
                    request.tenantId().value(), request.knowledgeSpaceId().value(),
                    request.dataSourceId().value(), request.contentVersion(), request.content(),
                    request.createdAt()));
            return new TextContentReference(value.version(), value.objectKey(), value.sha256(),
                    value.byteSize(), value.mediaType(), value.createdAt());
        }

        @Override
        public TextContentObjectMetadata metadata(TextContentReference reference) {
            return extension.metadata(text(reference));
        }

        @Override
        public InputStream read(TextContentReference reference) {
            return extension.read(text(reference));
        }

        @Override
        public void delete(TextContentReference reference) {
            extension.delete(text(reference));
        }

        private static TextObjectReference text(TextContentReference value) {
            return new TextObjectReference(value.version(), value.objectKey(), value.sha256(),
                    value.byteSize(), value.mediaType(), value.createdAt());
        }
    }

    private record CsvStorageBridge(CsvObjectStorageExtensionPort extension)
            implements StructuredDatasetCsvStoragePort {
        @Override
        public StructuredDatasetCsvReference put(StructuredDatasetCsvStorageRequest request) {
            var value = extension.put(new CsvObjectWriteRequest(request.tenantId().value(),
                    request.knowledgeSpaceId().value(), request.dataSourceId().value(),
                    request.revision(), request.content(), request.createdAt()));
            return new StructuredDatasetCsvReference(value.objectKey(), value.contentDigest(), value.byteSize());
        }

        @Override
        public InputStream read(StructuredDatasetCsvReference reference) {
            return extension.read(csv(reference));
        }

        @Override
        public void delete(StructuredDatasetCsvReference reference) {
            extension.delete(csv(reference));
        }

        private static CsvObjectReference csv(StructuredDatasetCsvReference value) {
            return new CsvObjectReference(value.objectKey(), value.contentDigest(), value.byteSize());
        }
    }

    private record PlatformReaderBridge(PlatformObjectContentReaderExtensionPort extension)
            implements PlatformObjectContentReaderPort {
        @Override
        public InputStream open(SourceRevision revision, PlatformObjectReference object) {
            Objects.requireNonNull(revision, "来源修订不能为空");
            Objects.requireNonNull(object, "平台对象不能为空");
            if (revision.sourceType() != GovernedDataSourceType.OBJECT_STORAGE
                    || revision.objectStorageObjects() == null
                    || !revision.objectStorageObjects().objects().contains(object)) {
                throw new IllegalArgumentException("平台对象不属于对象存储冻结修订");
            }
            var collection = revision.objectStorageObjects();
            return extension.open(new PlatformObjectReadRequest(
                    revision.tenantId().value(), revision.knowledgeSpaceId().value(),
                    collection.rangeId().value(), collection.selectionRevision(),
                    object.objectId(), object.contentDigest(), object.byteSize(), object.mediaType()));
        }
    }
}
