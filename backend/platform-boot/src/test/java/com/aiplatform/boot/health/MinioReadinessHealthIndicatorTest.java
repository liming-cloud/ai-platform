package com.aiplatform.boot.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.objectstorage.gateway.minio.config.ObjectStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class MinioReadinessHealthIndicatorTest {
    @Test
    void checksConfiguredBucketWithoutMutationAndDoesNotExposeStorageConfiguration() throws Exception {
        ObjectStorageProperties properties = properties();
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MinioReadinessHealthIndicator indicator = new MinioReadinessHealthIndicator(properties, minioClient);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
        ArgumentCaptor<BucketExistsArgs> argsCaptor = ArgumentCaptor.forClass(BucketExistsArgs.class);
        verify(minioClient).bucketExists(argsCaptor.capture());
        assertThat(argsCaptor.getValue().bucket()).isEqualTo("critical-bucket");
        verifyNoMoreInteractions(minioClient);
    }

    @Test
    void reportsDownForMissingBucketOrProbeFailureAndRecovers() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenReturn(false)
                .thenThrow(new IOException("endpoint=http://internal-minio, credential=never-expose"))
                .thenReturn(true);
        MinioReadinessHealthIndicator indicator = new MinioReadinessHealthIndicator(properties(), minioClient);

        Health missingBucket = indicator.health();
        Health unavailable = indicator.health();
        Health recovered = indicator.health();

        assertThat(missingBucket.getStatus()).isEqualTo(Status.DOWN);
        assertThat(unavailable.getStatus()).isEqualTo(Status.DOWN);
        assertThat(unavailable.getDetails()).isEmpty();
        assertThat(recovered.getStatus()).isEqualTo(Status.UP);
        verify(minioClient, times(3)).bucketExists(any(BucketExistsArgs.class));
        verifyNoMoreInteractions(minioClient);
    }

    private static ObjectStorageProperties properties() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setEndpoint("http://internal-minio:9000");
        properties.setAccessKey("internal-access-key");
        properties.setSecretKey("never-expose-secret");
        properties.setBucket("critical-bucket");
        properties.setRegion("us-east-1");
        return properties;
    }
}
