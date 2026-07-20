package com.aiplatform.knowledge.infrastructure.storage;

import com.aiplatform.infra.objectstorage.gateway.ObjectStorageGateway;
import com.aiplatform.infra.objectstorage.gateway.exception.ObjectStorageException;
import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageContent;
import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageObject;
import com.aiplatform.infra.objectstorage.gateway.model.StoredObject;
import com.aiplatform.knowledge.domain.DocumentObject;
import com.aiplatform.knowledge.domain.DocumentSourceContent;
import com.aiplatform.knowledge.domain.StoredDocumentObject;
import com.aiplatform.knowledge.domain.port.DocumentObjectStoragePort;
import com.aiplatform.knowledge.domain.port.DocumentSourceReaderPort;
import com.aiplatform.knowledge.infrastructure.storage.model.SourceLocation;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 知识文档对象存储适配器，负责业务对象 key 和元数据映射。
 */
@Component
public class KnowledgeDocumentObjectStorageAdapter implements DocumentObjectStoragePort, DocumentSourceReaderPort {
    private static final ErrorCode STORAGE_FAILED = ErrorCode.of("AIP-KB-STORAGE", "文档对象存储失败");

    private final ObjectStorageGateway objectStorageGateway;

    public KnowledgeDocumentObjectStorageAdapter(ObjectStorageGateway objectStorageGateway) {
        this.objectStorageGateway = Objects.requireNonNull(objectStorageGateway, "对象存储网关不能为空");
    }

    @Override
    public StoredDocumentObject store(DocumentObject documentObject) {
        Objects.requireNonNull(documentObject, "文档对象不能为空");
        try {
            StoredObject storedObject = objectStorageGateway.put(new ObjectStorageObject(
                    objectKey(documentObject),
                    documentObject.contentType(),
                    documentObject.contentHash(),
                    Map.of(
                            "tenant-id", documentObject.tenantId().value(),
                            "knowledge-base-id", documentObject.knowledgeBaseId(),
                            "document-id", documentObject.documentId(),
                            "content-sha256", documentObject.contentHash()
                    ),
                    documentObject.content()
            ));
            return new StoredDocumentObject(storedObject.bucket(), storedObject.objectKey(), storedObject.uri());
        } catch (ObjectStorageException exception) {
            throw new ApplicationException(STORAGE_FAILED, exception.getMessage());
        }
    }

    @Override
    public DocumentSourceContent read(String sourceUri) {
        SourceLocation sourceLocation = parseSourceUri(sourceUri);
        try {
            ObjectStorageContent content = objectStorageGateway.get(sourceLocation.bucket(), sourceLocation.objectKey());
            return new DocumentSourceContent(sourceUri, content.content());
        } catch (ObjectStorageException exception) {
            throw new ApplicationException(STORAGE_FAILED, exception.getMessage());
        }
    }

    private static String objectKey(DocumentObject documentObject) {
        return "tenants/%s/knowledge-bases/%s/documents/%s/%s/%s".formatted(
                documentObject.tenantId().value(),
                documentObject.knowledgeBaseId(),
                documentObject.documentId(),
                documentObject.contentHash(),
                sanitizeFileName(documentObject.fileName())
        );
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.trim().replace('\\', '_').replace('/', '_');
    }

    private static SourceLocation parseSourceUri(String sourceUri) {
        try {
            URI uri = new URI(sourceUri);
            if (!"s3".equals(uri.getScheme())) {
                throw new IllegalArgumentException("sourceUri 协议必须为 s3");
            }
            String bucket = uri.getHost();
            String path = uri.getPath();
            if (bucket == null || bucket.isBlank() || path == null || path.length() <= 1) {
                throw new IllegalArgumentException("sourceUri 必须包含 bucket 和 object key");
            }
            return new SourceLocation(bucket, path.substring(1));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new ApplicationException(STORAGE_FAILED, "非法 sourceUri=" + sourceUri);
        }
    }
}
