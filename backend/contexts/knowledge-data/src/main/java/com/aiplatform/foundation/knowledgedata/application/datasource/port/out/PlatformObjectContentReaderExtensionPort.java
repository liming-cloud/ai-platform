package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import java.io.InputStream;

/** 仅接收已核验冻结对象事实的平台对象读取扩展契约。 */
public interface PlatformObjectContentReaderExtensionPort {
    InputStream open(PlatformObjectReadRequest request);
}
