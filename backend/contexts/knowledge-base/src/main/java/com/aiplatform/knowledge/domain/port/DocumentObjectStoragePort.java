package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.DocumentObject;
import com.aiplatform.knowledge.domain.StoredDocumentObject;

/**
 * 知识文档原始文件对象存储端口。
 */
public interface DocumentObjectStoragePort {
    StoredDocumentObject store(DocumentObject documentObject);
}
