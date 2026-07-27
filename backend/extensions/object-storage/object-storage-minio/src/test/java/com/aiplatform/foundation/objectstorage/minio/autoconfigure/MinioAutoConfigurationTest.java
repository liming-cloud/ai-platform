package com.aiplatform.foundation.objectstorage.minio.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
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
                        "foundation.knowledge-data.object-storage.bucket=knowledge-private")
                .withBean(MinioClient.class, () -> client)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            TextContentObjectStoragePort.class);
                    assertThat(context.getBean(KnowledgeDataMinioProperties.class)
                            .getBucket()).isEqualTo("knowledge-private");
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
                });
    }

    @Test
    void 应用提供的对象存储端口不会被扩展覆盖() {
        var custom = mock(TextContentObjectStoragePort.class);
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
                .run(context -> assertThat(
                        context.getBean(TextContentObjectStoragePort.class))
                        .isSameAs(custom));
    }
}
