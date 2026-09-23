package com.aiplatform.foundation.knowledgedata.application.port.out.content;

import java.io.InputStream;

/** 私有文本对象存储扩展契约；领域引用只在知识上下文内部转换。 */
public interface TextObjectStorageExtensionPort {
    TextObjectReference put(TextObjectWriteRequest request);
    TextContentObjectMetadata metadata(TextObjectReference reference);
    InputStream read(TextObjectReference reference);
    void delete(TextObjectReference reference);
}
