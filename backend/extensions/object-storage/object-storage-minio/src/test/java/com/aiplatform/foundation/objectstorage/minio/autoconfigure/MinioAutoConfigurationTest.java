package com.aiplatform.foundation.objectstorage.minio.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MinioAutoConfigurationTest {

    @Test
    void 控制面启用后以类型安全配置注册对象存储端口() throws Exception {
        var client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MinioKnowledgeDataAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.knowledge-data.object-storage.enabled=true",
                        "foundation.knowledge-data.object-storage.endpoint=http://127.0.0.1:9000",
                        "foundation.knowledge-data.object-storage.access-key=test-access",
                        "foundation.knowledge-data.object-storage.secret-key=test-secret",
                        "foundation.knowledge-data.object-storage.bucket=knowledge-private",
                        "foundation.knowledge-data.object-storage.platform-object-locations[0].tenant-id=100000000000000001",
                        "foundation.knowledge-data.object-storage.platform-object-locations[0].knowledge-space-id=210000000000000001",
                        "foundation.knowledge-data.object-storage.platform-object-locations[0].range-id=rng_ObjectStorage001",
                        "foundation.knowledge-data.object-storage.platform-object-locations[0].selection-revision=1",
                        "foundation.knowledge-data.object-storage.platform-object-locations[0].object-id=obj_ObjectStorage001",
                        "foundation.knowledge-data.object-storage.platform-object-locations[0].object-key=platform-internal/object-storage/content-001.txt")
                .withBean(MinioClient.class, () -> client)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            TextContentObjectStoragePort.class);
                    assertThat(context).hasSingleBean(
                            StructuredDatasetCsvStoragePort.class);
                    assertThat(context).hasSingleBean(
                            PlatformObjectContentReaderPort.class);
                    assertThat(context.getBean(KnowledgeDataMinioProperties.class)
                            .getBucket()).isEqualTo("knowledge-private");
                    assertThat(context.getBean(KnowledgeDataMinioProperties.class)
                            .getPlatformObjectLocations()).singleElement().satisfies(location ->
                                    assertThat(location.objectKey()).isEqualTo(
                                            "platform-internal/object-storage/content-001.txt"));
                });
    }

    @Test
    void 控制面关闭时不注册客户端和端口() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MinioKnowledgeDataAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(
                            TextContentObjectStoragePort.class);
                    assertThat(context).doesNotHaveBean(
                            StructuredDatasetCsvStoragePort.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformObjectContentReaderPort.class);
                });
    }

    @Test
    void 应用提供的对象存储端口不会被扩展覆盖() {
        var custom = mock(TextContentObjectStoragePort.class);
        var customCsv = mock(StructuredDatasetCsvStoragePort.class);
        var customReader = mock(PlatformObjectContentReaderPort.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MinioKnowledgeDataAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.knowledge-data.object-storage.enabled=true",
                        "foundation.knowledge-data.object-storage.endpoint=http://127.0.0.1:9000",
                        "foundation.knowledge-data.object-storage.access-key=test-access",
                        "foundation.knowledge-data.object-storage.secret-key=test-secret",
                        "foundation.knowledge-data.object-storage.bucket=knowledge-private")
                .withBean(TextContentObjectStoragePort.class, () -> custom)
                .withBean(StructuredDatasetCsvStoragePort.class, () -> customCsv)
                .withBean(PlatformObjectContentReaderPort.class, () -> customReader)
                .run(context -> {
                    assertThat(context.getBean(TextContentObjectStoragePort.class))
                            .isSameAs(custom);
                    assertThat(context.getBean(StructuredDatasetCsvStoragePort.class))
                            .isSameAs(customCsv);
                    assertThat(context.getBean(PlatformObjectContentReaderPort.class))
                            .isSameAs(customReader);
                });
    }
}
