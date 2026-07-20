package com.aiplatform.infra.objectstorage.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectStorageModelTest {
    @Test
    void normalizesStoredObjectFactsBeforeBusinessUse() {
        StoredObject storedObject = new StoredObject(
                " aip-knowledge ",
                " tenants/2001/docs/file.txt ",
                " s3://aip-knowledge/tenants/2001/docs/file.txt "
        );

        assertThat(storedObject.bucket()).isEqualTo("aip-knowledge");
        assertThat(storedObject.objectKey()).isEqualTo("tenants/2001/docs/file.txt");
        assertThat(storedObject.uri()).isEqualTo("s3://aip-knowledge/tenants/2001/docs/file.txt");
    }

    @Test
    void normalizesObjectStorageObjectFactsMetadataAndCopiesContent() {
        byte[] content = "制度内容".getBytes(StandardCharsets.UTF_8);
        ObjectStorageObject object = new ObjectStorageObject(
                " tenants/2001/docs/file.txt ",
                " text/plain ",
                " abc123 ",
                Map.of(" tenantId ", " 2001 "),
                content
        );
        content[0] = 'X';

        assertThat(object.objectKey()).isEqualTo("tenants/2001/docs/file.txt");
        assertThat(object.contentType()).isEqualTo("text/plain");
        assertThat(object.contentSha256()).isEqualTo("abc123");
        assertThat(object.metadata()).containsEntry("tenantId", "2001");
        assertThat(new String(object.content(), StandardCharsets.UTF_8)).isEqualTo("制度内容");
    }

    @Test
    void normalizesObjectStorageContentFactsAndCopiesContent() {
        byte[] content = "制度内容".getBytes(StandardCharsets.UTF_8);
        ObjectStorageContent storedContent = new ObjectStorageContent(
                " aip-knowledge ",
                " tenants/2001/docs/file.txt ",
                content
        );
        content[0] = 'X';

        assertThat(storedContent.bucket()).isEqualTo("aip-knowledge");
        assertThat(storedContent.objectKey()).isEqualTo("tenants/2001/docs/file.txt");
        assertThat(new String(storedContent.content(), StandardCharsets.UTF_8)).isEqualTo("制度内容");
    }

    @Test
    void rejectsEmptyObjectContentInsteadOfPersistingEmptyObject() {
        assertThatThrownBy(() -> new ObjectStorageObject(
                "tenants/2001/docs/file.txt",
                "text/plain",
                "abc123",
                Map.of(),
                new byte[0]
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象内容不能为空");

        assertThatThrownBy(() -> new ObjectStorageContent(
                "aip-knowledge",
                "tenants/2001/docs/file.txt",
                new byte[0]
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象内容不能为空");
    }

    @Test
    void rejectsMissingObjectStorageFactsWithChineseMessages() {
        assertThatThrownBy(() -> new StoredObject(null, "tenants/2001/docs/file.txt", "s3://aip-knowledge/tenants/2001/docs/file.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket不能为空");
        assertThatThrownBy(() -> new ObjectStorageObject(
                null,
                "text/plain",
                "abc123",
                Map.of(),
                "制度内容".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectKey不能为空");
        assertThatThrownBy(() -> new ObjectStorageObject(
                "tenants/2001/docs/file.txt",
                "text/plain",
                "abc123",
                null,
                "制度内容".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象元数据不能为空");
        assertThatThrownBy(() -> new ObjectStorageObject(
                "tenants/2001/docs/file.txt",
                "text/plain",
                "abc123",
                Map.of(),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象内容不能为空");
        assertThatThrownBy(() -> new ObjectStorageContent("aip-knowledge", "tenants/2001/docs/file.txt", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象内容不能为空");
    }

    @Test
    void rejectsBlankObjectMetadataInsteadOfWritingInvalidMetadata() {
        assertThatThrownBy(() -> new ObjectStorageObject(
                "tenants/2001/docs/file.txt",
                "text/plain",
                "abc123",
                Map.of(" ", "2001"),
                "制度内容".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象元数据key不能为空白");
    }
}
