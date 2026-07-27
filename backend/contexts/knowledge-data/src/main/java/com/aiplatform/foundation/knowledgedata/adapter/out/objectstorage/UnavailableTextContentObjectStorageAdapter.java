package com.aiplatform.foundation.knowledgedata.adapter.out.objectstorage;

import com.aiplatform.foundation.knowledgedata.application.content.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import java.io.InputStream;

/**
 * 未配置正式对象存储时拒绝所有内容操作，避免产生伪成功。
 */
public final class UnavailableTextContentObjectStorageAdapter
        implements TextContentObjectStoragePort {

    @Override
    public TextContentReference put(TextContentStorageRequest request) {
        throw new ObjectStorageUnavailableException();
    }

    @Override
    public TextContentObjectMetadata metadata(TextContentReference reference) {
        throw new ObjectStorageUnavailableException();
    }

    @Override
    public InputStream read(TextContentReference reference) {
        throw new ObjectStorageUnavailableException();
    }

    @Override
    public void delete(TextContentReference reference) {
        throw new ObjectStorageUnavailableException();
    }
}
