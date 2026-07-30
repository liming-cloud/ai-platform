package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import com.aiplatform.foundation.knowledgedata.application.datasource.exception.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import java.io.InputStream;

/** 未配置私有对象存储时拒绝结构化数据集 CSV 操作，避免产生伪成功。 */
public final class UnavailableStructuredDatasetCsvStorageAdapter implements StructuredDatasetCsvStoragePort {

    @Override
    public StructuredDatasetCsvReference put(StructuredDatasetCsvStorageRequest request) {
        throw new ObjectStorageUnavailableException();
    }

    @Override
    public InputStream read(StructuredDatasetCsvReference reference) {
        throw new ObjectStorageUnavailableException();
    }

    @Override
    public void delete(StructuredDatasetCsvReference reference) {
        throw new ObjectStorageUnavailableException();
    }
}
