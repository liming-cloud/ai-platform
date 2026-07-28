package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 按管理权或当前使用权读取模型配置，跨范围资源统一按不存在处理。
 */
public final class ModelConfigurationReadService {

    private final ModelConfigurationRepository configurations;
    private final ModelConfigurationService management;
    private final ModelConfigurationAccessService access;
    private final ModelConfigurationCredentialInspector credentials;

    public ModelConfigurationReadService(
            ModelConfigurationRepository configurations,
            ModelConfigurationService management,
            ModelConfigurationAccessService access,
            ModelConfigurationCredentialInspector credentials) {
        this.configurations = Objects.requireNonNull(configurations, "模型配置仓储不能为空");
        this.management = Objects.requireNonNull(management, "模型配置管理用例不能为空");
        this.access = Objects.requireNonNull(access, "模型配置授权用例不能为空");
        this.credentials = Objects.requireNonNull(credentials, "模型配置凭据状态查询不能为空");
    }

    public ModelConfigurationReadResult get(
            TrustedRequestContext context, ModelConfigurationId configurationId) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        var configuration = configurations.findConfiguration(
                        Objects.requireNonNull(configurationId, "模型配置标识不能为空"))
                .orElseThrow(ModelConfigurationNotFoundException::new);
        if (!management.mayManage(context, configuration) && !access.mayUse(context, configuration)) {
            throw new ModelConfigurationNotFoundException();
        }
        return new ModelConfigurationReadResult(
                configuration, credentials.isConfigured(configuration.id()));
    }
}
