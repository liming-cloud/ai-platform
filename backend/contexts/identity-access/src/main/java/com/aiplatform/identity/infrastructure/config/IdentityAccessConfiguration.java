package com.aiplatform.identity.infrastructure.config;

import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.feature.port.TenantFeatureAccessPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 身份上下文基础 Bean 配置，避免应用服务直接创建运行时依赖。
 */
@Configuration
public class IdentityAccessConfiguration {
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public TenantFeatureGuard tenantFeatureGuard(TenantFeatureAccessPort accessPort) {
        return new TenantFeatureGuard(accessPort);
    }
}
