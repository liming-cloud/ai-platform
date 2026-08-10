package com.aiplatform.foundation.modelruntime.application.provider.service;

import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryCandidateReader;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Clock;
import java.util.Objects;

/** 在可信租户范围分页读取当前连接修订可用的模型候选。 */
public final class ProviderModelDiscoveryCandidateReadService {

    private static final PermissionCode READ = new PermissionCode("model:read");

    private final ProviderConnectionRepository connections;
    private final ProviderModelDiscoveryCandidateReader candidates;
    private final Clock clock;

    public ProviderModelDiscoveryCandidateReadService(
            ProviderConnectionRepository connections,
            ProviderModelDiscoveryCandidateReader candidates,
            Clock clock) {
        this.connections = Objects.requireNonNull(connections, "供应商连接仓储不能为空");
        this.candidates = Objects.requireNonNull(candidates, "模型发现候选读取端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 返回当前已验证连接的未过期候选；历史候选与供应商诊断不通过本接口暴露。
     *
     * @throws ModelCatalogPermissionDeniedException 当前成员没有 `model:read` 时抛出。
     * @throws ProviderConnectionNotFoundException 连接不属于当前租户或不存在时抛出。
     * @throws IllegalStateException 连接当前修订尚未验证时抛出。
     */
    public PageResult<ProviderModelCandidateView> listCurrentCandidates(
            TrustedRequestContext context,
            String providerConnectionId,
            PageQuery page) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页参数不能为空");
        if (!context.hasPermission(READ)) {
            throw new ModelCatalogPermissionDeniedException();
        }
        var connectionId = ProviderConnectionId.parse(providerConnectionId);
        var connection = connections.findById(context.tenantId(), connectionId)
                .orElseThrow(ProviderConnectionNotFoundException::new);
        if (connection.status() != ProviderConnectionStatus.VERIFIED) {
            throw new IllegalStateException("供应商连接尚未验证");
        }
        var result = candidates.findCurrentCandidates(
                context.tenantId(), connectionId, connection.version(),
                page.pageNumber(), page.pageSize(), clock.instant());
        return PageResult.of(
                result.items().stream()
                        .map(candidate -> new ProviderModelCandidateView(
                                candidate.modelCode(), candidate.modelType(), candidate.capabilities()))
                        .toList(),
                page,
                result.totalElements());
    }
}
