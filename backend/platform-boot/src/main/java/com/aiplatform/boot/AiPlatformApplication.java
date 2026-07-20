package com.aiplatform.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan({
        "com.aiplatform.identity.infrastructure.persistence.mapper",
        "com.aiplatform.modelhub.infrastructure.persistence.mapper",
        "com.aiplatform.knowledge.infrastructure.persistence.mapper",
        "com.aiplatform.appstudio.infrastructure.persistence.mapper",
        "com.aiplatform.approval.infrastructure.persistence.mapper",
        "com.aiplatform.audit.infrastructure.persistence.mapper",
        "com.aiplatform.summary.infrastructure.persistence.mapper"
})
@SpringBootApplication(scanBasePackages = "com.aiplatform")
public class AiPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
    }
}
