package com.aiplatform.boot;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

class AiPlatformApplicationTest {
    @Test
    void applicationClassIsAvailable() {
        AiPlatformApplication application = new AiPlatformApplication();
        org.junit.jupiter.api.Assertions.assertNotNull(application);
    }

    @Test
    void scansContextMappers() {
        MapperScan mapperScan = AiPlatformApplication.class.getAnnotation(MapperScan.class);

        org.junit.jupiter.api.Assertions.assertNotNull(mapperScan);
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{
                "com.aiplatform.identity.infrastructure.persistence.mapper",
                "com.aiplatform.modelhub.infrastructure.persistence.mapper",
                "com.aiplatform.knowledge.infrastructure.persistence.mapper",
                "com.aiplatform.appstudio.infrastructure.persistence.mapper",
                "com.aiplatform.approval.infrastructure.persistence.mapper",
                "com.aiplatform.audit.infrastructure.persistence.mapper",
                "com.aiplatform.summary.infrastructure.persistence.mapper"
        }, mapperScan.value());
    }
}
