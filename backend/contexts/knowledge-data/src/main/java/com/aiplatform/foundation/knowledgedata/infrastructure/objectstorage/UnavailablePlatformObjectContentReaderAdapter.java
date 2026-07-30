package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import com.aiplatform.foundation.knowledgedata.application.datasource.exception.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderPort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import java.io.InputStream;

/** 未配置平台对象正文读取能力时的失败关闭实现。 */
public final class UnavailablePlatformObjectContentReaderAdapter implements PlatformObjectContentReaderPort {
    @Override
    public InputStream open(SourceRevision revision, PlatformObjectReference object) {
        throw new ObjectStorageUnavailableException();
    }
}
