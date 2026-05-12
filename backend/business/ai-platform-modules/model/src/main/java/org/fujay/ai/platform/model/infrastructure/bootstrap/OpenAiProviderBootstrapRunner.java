package org.fujay.ai.platform.model.infrastructure.bootstrap;

import java.util.List;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand.ModelCommand;
import org.fujay.ai.platform.model.application.service.ModelProviderApplicationService;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.shared.tenant.TenantIdentityResolver;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class OpenAiProviderBootstrapRunner implements ApplicationRunner {

    private final OpenAiProviderBootstrapProperties properties;
    private final ModelProviderApplicationService modelProviderApplicationService;
    private final TenantIdentityResolver tenantIdentityResolver;

    public OpenAiProviderBootstrapRunner(
            OpenAiProviderBootstrapProperties properties,
            ModelProviderApplicationService modelProviderApplicationService,
            TenantIdentityResolver tenantIdentityResolver) {
        this.properties = properties;
        this.modelProviderApplicationService = modelProviderApplicationService;
        this.tenantIdentityResolver = tenantIdentityResolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            return;
        }
        TenantId tenantId = tenantIdentityResolver
                .findTenantIdByCode(properties.tenantCode())
                .orElseThrow(() -> new IllegalStateException("未找到模型启动配置对应的租户编码：" + properties.tenantCode()));
        modelProviderApplicationService.bootstrapProviderIfAbsent(tenantId.value(), bootstrapCommand());
    }

    private SaveModelProviderCommand bootstrapCommand() {
        return new SaveModelProviderCommand(
                properties.providerName(),
                ProviderType.OPENAI,
                "OFFICIAL_DEFAULT",
                properties.apiKey(),
                properties.budgetUsd(),
                List.of(new ModelCommand(
                        properties.chatModel(),
                        properties.chatModel(),
                        ModelCapabilityType.CHAT,
                        new ModelParameterDefaults(
                                properties.contextWindow(),
                                properties.maxOutputTokens(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                properties.reasoningEffort()))));
    }
}
