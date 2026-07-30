package com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import java.io.InputStream;

/** 平台私有 UTF-8 CSV 的最小受治理存储端口。 */
public interface StructuredDatasetCsvStoragePort {

    StructuredDatasetCsvReference put(StructuredDatasetCsvStorageRequest request);

    /** 打开冻结 CSV 正文；调用方负责关闭返回流。 */
    InputStream read(StructuredDatasetCsvReference reference);

    void delete(StructuredDatasetCsvReference reference);
}
