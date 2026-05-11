package org.fujay.ai.platform.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan("org.fujay.ai.platform")
@MapperScan({
    "org.fujay.ai.platform.tenant.infrastructure.persistence.mapper",
    "org.fujay.ai.platform.model.infrastructure.persistence.mapper"
})
@SpringBootApplication(scanBasePackages = "org.fujay.ai.platform")
public class AiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
    }
}
