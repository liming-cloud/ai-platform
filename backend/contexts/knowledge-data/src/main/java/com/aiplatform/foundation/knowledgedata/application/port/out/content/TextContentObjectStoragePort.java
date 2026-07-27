package com.aiplatform.foundation.knowledgedata.application.port.out.content;

import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import java.io.InputStream;

/**
 * 私有对象存储的最小受治理端口。
 */
public interface TextContentObjectStoragePort {

    TextContentReference put(TextContentStorageRequest request);

    TextContentObjectMetadata metadata(TextContentReference reference);

    /**
     * 打开对象内容流；调用方负责关闭返回的流。
     */
    InputStream read(TextContentReference reference);

    void delete(TextContentReference reference);
}
