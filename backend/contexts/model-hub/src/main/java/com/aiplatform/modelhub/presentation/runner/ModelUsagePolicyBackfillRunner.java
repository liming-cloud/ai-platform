package com.aiplatform.modelhub.presentation.runner;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动入口：重放现存模型策略事件，补齐 knowledge-base 等下游上下文的本地投影。
 * 作为 Spring Boot 入站 runner 放在 presentation 层，避免 application 层直接承担运行时入口职责。
 */
@Component
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.domainEvent-out-0.destination")
public class ModelUsagePolicyBackfillRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ModelUsagePolicyBackfillRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final ModelProviderRepository providers;
    private final ModelConfigRepository configs;
    private final ModelUsagePolicyPublisher usagePolicyPublisher;

    public ModelUsagePolicyBackfillRunner(
            JdbcTemplate jdbcTemplate,
            ModelProviderRepository providers,
            ModelConfigRepository configs,
            ModelUsagePolicyPublisher usagePolicyPublisher
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate 不能为空");
        this.providers = Objects.requireNonNull(providers, "模型供应商仓储不能为空");
        this.configs = Objects.requireNonNull(configs, "模型配置仓储不能为空");
        this.usagePolicyPublisher = Objects.requireNonNull(usagePolicyPublisher, "模型使用策略发布器不能为空");
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> tenantIds = jdbcTemplate.query(
                "select distinct tenant_id::text from mdl_provider order by tenant_id",
                (rs, rowNum) -> rs.getString(1)
        );
        int published = 0;
        for (String tenantValue : tenantIds) {
            TenantId tenantId = TenantId.of(tenantValue);
            for (ModelProvider provider : providers.findAll(tenantId)) {
                published += republishProviderConfigs(tenantId, provider);
            }
        }
        log.info("模型使用策略启动重放完成，tenantCount={}, publishedCount={}", tenantIds.size(), published);
    }

    private int republishProviderConfigs(TenantId tenantId, ModelProvider provider) {
        int published = 0;
        for (ModelConfig config : configs.findByProviderId(tenantId, provider.providerId())) {
            usagePolicyPublisher.publish(provider, config);
            published++;
        }
        return published;
    }
}
