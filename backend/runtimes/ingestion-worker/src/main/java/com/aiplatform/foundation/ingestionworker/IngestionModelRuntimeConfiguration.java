package com.aiplatform.foundation.ingestionworker;

import com.aiplatform.foundation.modelruntime.adapter.out.persistence.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.PostgresModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.PostgresProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.runtime.DefaultModelRuntimeResolver;
import com.aiplatform.foundation.provider.deterministic.DeterministicEmbeddingModelFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 索引摄取进程按租户和冻结修订解析已发布嵌入模型。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.ingestion.enabled",
        havingValue = "true")
@MapperScan(basePackageClasses = ProviderConnectionMapper.class)
public class IngestionModelRuntimeConfiguration {

    @Bean
    ProviderConnectionRepository ingestionProviderConnectionRepository(
            ProviderConnectionMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresProviderConnectionRepository(mapper, transactions);
    }

    @Bean
    ModelDeploymentRepository ingestionModelDeploymentRepository(
            ModelDeploymentMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresModelDeploymentRepository(mapper, transactions);
    }

    @Bean
    ModelRuntimeResolver ingestionModelRuntimeResolver(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers) {
        return new DefaultModelRuntimeResolver(
                deployments,
                providers,
                List.of(),
                List.of(new DeterministicEmbeddingModelFactory()),
                64,
                Duration.ofMinutes(30),
                Clock.systemUTC());
    }
}
