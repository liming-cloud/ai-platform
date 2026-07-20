package com.aiplatform.knowledge.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.objectstorage.gateway.ObjectStorageGateway;
import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageContent;
import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageObject;
import com.aiplatform.infra.objectstorage.gateway.model.StoredObject;
import com.aiplatform.knowledge.domain.DocumentObject;
import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeDocumentObjectStorageAdapterTest {
    @Test
    void mapsKnowledgeDocumentToInfrastructureObjectStorageObject() {
        ObjectStorageGateway gateway = mock(ObjectStorageGateway.class);
        when(gateway.put(any(ObjectStorageObject.class))).thenAnswer(invocation -> {
            ObjectStorageObject object = invocation.getArgument(0);
            return new StoredObject("aip-knowledge", object.objectKey(), "s3://aip-knowledge/" + object.objectKey());
        });
        KnowledgeDocumentObjectStorageAdapter adapter = new KnowledgeDocumentObjectStorageAdapter(gateway);

        var stored = adapter.store(new DocumentObject(
                TenantId.of("2001"),
                "7001",
                "8001",
                "folder/员工手册.txt",
                "text/plain",
                "abc123",
                "制度内容".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(stored.sourceUri()).isEqualTo("s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/abc123/folder_员工手册.txt");
        ArgumentCaptor<ObjectStorageObject> objectCaptor = ArgumentCaptor.forClass(ObjectStorageObject.class);
        verify(gateway).put(objectCaptor.capture());
        ObjectStorageObject captured = objectCaptor.getValue();
        assertThat(captured.objectKey()).isEqualTo("tenants/2001/knowledge-bases/7001/documents/8001/abc123/folder_员工手册.txt");
        assertThat(captured.metadata())
                .containsEntry("tenant-id", "2001")
                .containsEntry("knowledge-base-id", "7001")
                .containsEntry("document-id", "8001")
                .containsEntry("content-sha256", "abc123");
        assertThat(captured.content()).containsExactly("制度内容".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsDocumentFromS3SourceUriThroughInfrastructureGateway() {
        ObjectStorageGateway gateway = mock(ObjectStorageGateway.class);
        when(gateway.get("aip-knowledge", "tenants/2001/documents/员工 手册.txt"))
                .thenReturn(new ObjectStorageContent(
                        "aip-knowledge",
                        "tenants/2001/documents/员工 手册.txt",
                        "制度内容".getBytes(StandardCharsets.UTF_8)
                ));
        KnowledgeDocumentObjectStorageAdapter adapter = new KnowledgeDocumentObjectStorageAdapter(gateway);

        var content = adapter.read("s3://aip-knowledge/tenants/2001/documents/%E5%91%98%E5%B7%A5%20%E6%89%8B%E5%86%8C.txt");

        verify(gateway).get("aip-knowledge", "tenants/2001/documents/员工 手册.txt");
        assertThat(content.sourceUri()).isEqualTo("s3://aip-knowledge/tenants/2001/documents/%E5%91%98%E5%B7%A5%20%E6%89%8B%E5%86%8C.txt");
        assertThat(content.content()).containsExactly("制度内容".getBytes(StandardCharsets.UTF_8));
    }
}
