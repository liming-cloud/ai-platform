package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;

/**
 * 私有对象存储的最小受治理端口。
 */
public interface TextContentObjectStoragePort {

    TextContentReference put(TextContentStorageRequest request);

    TextContentObjectMetadata metadata(TextContentReference reference);

    void delete(TextContentReference reference);
}
