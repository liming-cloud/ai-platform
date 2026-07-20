package com.aiplatform.knowledge.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.aiplatform.infra.objectstorage.gateway.minio.config.ObjectStorageProperties;
import com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask;
import com.aiplatform.knowledge.domain.maintenance.DocumentObjectCleanupOwnership;
import com.aiplatform.shared.kernel.TenantId;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MinioDocumentObjectDeletionAdapterTest {
    @Test
    void deletesOnlyTheExactConfiguredBucketAndTenantPath() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioDocumentObjectDeletionAdapter adapter = new MinioDocumentObjectDeletionAdapter(properties(), client);

        adapter.delete(task("2001", "aip-knowledge",
                "tenants/2001/knowledge-bases/7001/documents/8001/hash/handbook.txt"));

        ArgumentCaptor<RemoveObjectArgs> args = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(args.capture());
        org.assertj.core.api.Assertions.assertThat(args.getValue().bucket()).isEqualTo("aip-knowledge");
        org.assertj.core.api.Assertions.assertThat(args.getValue().object())
                .isEqualTo("tenants/2001/knowledge-bases/7001/documents/8001/hash/handbook.txt");
    }

    @Test
    void rejectsCrossTenantKeyBeforeCallingMinio() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioDocumentObjectDeletionAdapter adapter = new MinioDocumentObjectDeletionAdapter(properties(), client);

        assertThatThrownBy(() -> adapter.delete(task("2001", "aip-knowledge",
                "tenants/2002/knowledge-bases/7001/documents/8001/hash/handbook.txt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户对象路径");
        verify(client, never()).removeObject(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsTheCanonicalPercentEncodedS3UriForTheExactRawObjectKey() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioDocumentObjectDeletionAdapter adapter = new MinioDocumentObjectDeletionAdapter(properties(), client);
        String key = "tenants/2001/knowledge-bases/7001/documents/8001/hash/员工 手册.txt";
        ClaimedDocumentObjectCleanupTask task = task(
                "2001",
                "aip-knowledge",
                key,
                "s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash/员工%20手册.txt"
        );

        adapter.delete(task);

        ArgumentCaptor<RemoveObjectArgs> args = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(args.capture());
        org.assertj.core.api.Assertions.assertThat(args.getValue().object()).isEqualTo(key);
    }

    @Test
    void rejectsADeceptiveNonCanonicalEncodedPathSeparator() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioDocumentObjectDeletionAdapter adapter = new MinioDocumentObjectDeletionAdapter(properties(), client);
        String key = "tenants/2001/knowledge-bases/7001/documents/8001/hash/folder/file.txt";
        ClaimedDocumentObjectCleanupTask task = task(
                "2001",
                "aip-knowledge",
                key,
                "s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash%2Ffolder/file.txt"
        );

        assertThatThrownBy(() -> adapter.delete(task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URI");
        verify(client, never()).removeObject(org.mockito.ArgumentMatchers.any());
    }

    private static ObjectStorageProperties properties() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setEndpoint("http://127.0.0.1:9000");
        properties.setAccessKey("minio-access");
        properties.setSecretKey("minio-secret-value");
        properties.setBucket("aip-knowledge");
        properties.setRegion("us-east-1");
        return properties;
    }

    private static ClaimedDocumentObjectCleanupTask task(String tenant, String bucket, String key) {
        return task(tenant, bucket, key, "s3://" + bucket + "/" + key);
    }

    private static ClaimedDocumentObjectCleanupTask task(
            String tenant,
            String bucket,
            String key,
            String sourceUri
    ) {
        TenantId tenantId = TenantId.of(tenant);
        return new ClaimedDocumentObjectCleanupTask(
                tenantId, "9101", "7001", "8001", bucket, key,
                sourceUri, "DataIntegrityViolationException", 1,
                new DocumentObjectCleanupOwnership(tenantId, "9101", "worker-a", "lease-a", 1)
        );
    }
}
