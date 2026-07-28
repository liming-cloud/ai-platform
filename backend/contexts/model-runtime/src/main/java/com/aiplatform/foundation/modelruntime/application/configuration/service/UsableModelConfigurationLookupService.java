package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import java.util.Optional;

/**
 * 解析当前主体可实际调用的受控模型配置。
 *
 * <p>该服务只投影冻结标识和能力，凭据是否已配置仅用于失败关闭，绝不离开凭据端口。</p>
 */
public final class UsableModelConfigurationLookupService
        implements UsableModelConfigurationLookup {

    private final ModelConfigurationRepository configurations;
    private final ModelConfigurationAccessService access;
    private final ModelConfigurationCredentialInspector credentials;

    public UsableModelConfigurationLookupService(
            ModelConfigurationRepository configurations,
            ModelConfigurationAccessService access,
            ModelConfigurationCredentialInspector credentials) {
        this.configurations = Objects.requireNonNull(configurations, "模型配置仓储不能为空");
        this.access = Objects.requireNonNull(access, "模型配置授权用例不能为空");
        this.credentials = Objects.requireNonNull(credentials, "模型配置凭据状态查询不能为空");
    }

    /**
     * 返回已授权、已启用、已配置凭据且目录类型匹配的模型快照；任何缺失都按不可用处理。
     */
    @Override
    public Optional<UsableModelConfigurationSnapshot> find(
            TrustedRequestContext context,
            ModelConfigurationId configurationId,
            ModelType requiredType) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(configurationId, "模型配置标识不能为空");
        Objects.requireNonNull(requiredType, "所需模型类型不能为空");
        var configuration = configurations.findConfiguration(configurationId);
        if (configuration.isEmpty()
                || !access.mayUse(context, configuration.get())
                || !credentials.isConfigured(configurationId)) {
            return Optional.empty();
        }
        return configurations.findCatalogEntry(configuration.get().catalogEntryId())
                .filter(entry -> entry.status() == ControlledModelCatalogEntryStatus.PUBLISHED)
                .filter(entry -> entry.modelType() == requiredType)
                .map(entry -> new UsableModelConfigurationSnapshot(
                        configurationId,
                        configuration.get().version(),
                        entry.id(),
                        entry.version(),
                        entry.modelType(),
                        entry.capabilities()));
    }
}
