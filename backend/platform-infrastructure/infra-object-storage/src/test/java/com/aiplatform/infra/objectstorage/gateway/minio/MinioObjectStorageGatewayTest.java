package com.aiplatform.infra.objectstorage.gateway.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.objectstorage.gateway.ObjectStorageGateway;
import com.aiplatform.infra.objectstorage.gateway.minio.config.ObjectStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MinioObjectStorageGatewayTest {
    private ObjectStorageProperties properties;
    private MinioClient minioClient;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(ObjectStorageProperties.class)
            .withBean(MinioObjectStorageGateway.class)
            .withPropertyValues(
                    "aip.object-storage.endpoint=http://localhost:9000",
                    "aip.object-storage.access-key=minio-access-2026",
                    "aip.object-storage.secret-key=" + "q".repeat(32),
                    "aip.object-storage.bucket=aip-knowledge",
                    "aip.object-storage.region=us-east-1"
            );

    @BeforeEach
    void setUp() {
        properties = new ObjectStorageProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("minio-access-2026");
        properties.setSecretKey("0123456789abcdef0123456789abcdef");
        properties.setBucket("aip-knowledge");
        properties.setRegion("us-east-1");
        minioClient = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
    }

    @Test
    void gatewayBeanUsesConfigurationPropertiesConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectStorageGateway.class);
            assertThat(context).hasSingleBean(MinioObjectStorageGateway.class);
        });
    }

    @Test
    void s3UriEscapesObjectKeyCharactersThatAreNotValidInUris() {
        assertThat(MinioObjectStorageGateway.s3Uri("aip-knowledge", "tenants/2001/docs/file name#1.txt"))
                .isEqualTo("s3://aip-knowledge/tenants/2001/docs/file%20name%231.txt");
    }

    @Test
    void s3UriNormalizesStorageLocationFactsBeforeBuildingUri() {
        assertThat(MinioObjectStorageGateway.s3Uri(" aip-knowledge ", " tenants/2001/docs/file.txt "))
                .isEqualTo("s3://aip-knowledge/tenants/2001/docs/file.txt");
    }

    @Test
    void getNormalizesStorageLocationFactsBeforeCallingMinio() throws Exception {
        GetObjectArgs args = MinioObjectStorageGateway.getObjectArgs(
                " aip-knowledge ",
                " tenants/2001/docs/file.txt "
        );

        assertThat(args.bucket()).isEqualTo("aip-knowledge");
        assertThat(args.object()).isEqualTo("tenants/2001/docs/file.txt");
    }

    @Test
    void rejectsBlankStorageLookupFactsBeforeCallingMinio() {
        assertThatThrownBy(() -> MinioObjectStorageGateway.getObjectArgs(" ", "tenants/2001/docs/file.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket不能为空白");
    }

    @Test
    void rejectsMissingStorageLookupFactsWithChineseMessages() {
        assertThatThrownBy(() -> MinioObjectStorageGateway.getObjectArgs(null, "tenants/2001/docs/file.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket不能为空");
        assertThatThrownBy(() -> MinioObjectStorageGateway.s3Uri("aip-knowledge", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectKey不能为空");
    }

    @Test
    void rejectsMissingGatewayDependenciesAndObjectWithChineseMessages() {
        assertThatThrownBy(() -> new MinioObjectStorageGateway(null, minioClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象存储配置不能为空");
        assertThatThrownBy(() -> new MinioObjectStorageGateway(properties, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MinIO 客户端不能为空");

        MinioObjectStorageGateway gateway = new MinioObjectStorageGateway(properties, minioClient);
        assertThatThrownBy(() -> gateway.put(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("待写入对象不能为空");
    }
}
