package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.application.datasource.exception.ObjectStorageUnavailableException;
import org.junit.jupiter.api.Test;

class UnavailablePlatformObjectContentReaderAdapterTest {

    @Test
    void 未配置读取能力时仍必须以冻结来源修订和对象事实关闭失败() {
        assertThrows(ObjectStorageUnavailableException.class,
                () -> new UnavailablePlatformObjectContentReaderAdapter().open(null, null));
    }
}
