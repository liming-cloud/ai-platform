package com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset;

import java.io.InputStream;

/** 私有 CSV 对象存储扩展契约。 */
public interface CsvObjectStorageExtensionPort {
    CsvObjectReference put(CsvObjectWriteRequest request);
    InputStream read(CsvObjectReference reference);
    void delete(CsvObjectReference reference);
}
