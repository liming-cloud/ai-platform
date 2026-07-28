package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.application.configuration.dto.ModelConfigurationListItem;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationPage;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
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

    /**
     * 按当前主体的配置使用范围分页列出安全摘要，禁用、草稿与未配置凭据状态均明确投影。
     */
    public PageResult<ModelConfigurationListItem> list(
            TrustedRequestContext context, PageQuery page) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页参数不能为空");
        ModelConfigurationPage result = configurations.findVisiblePage(
                context.tenantId(),
                new ModelConfigurationOwnerId(context.membershipId().value()),
                page.pageNumber(),
                page.pageSize());
        return PageResult.of(
                result.items().stream()
                        .map(configuration -> configurations.findCatalogEntry(configuration.catalogEntryId())
                                .map(entry -> ModelConfigurationListItem.from(
                                        configuration,
                                        entry.modelType(),
                                        credentials.isConfigured(configuration.id())))
                                .orElseThrow(() -> new IllegalStateException("模型配置引用的目录条目不存在")))
                        .toList(),
                page,
                result.totalElements());
    }
}
