package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderModelCandidatesQuery;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Clock;
import java.util.Objects;

/** 读取可供平台管理员选择的当前发现候选。 */
public final class PlatformProviderModelCandidateReadService {
    private final PlatformProviderConnectionRepository connections;
    private final PlatformProviderModelDiscoveryLookup discoveries;
    private final Clock clock;

    public PlatformProviderModelCandidateReadService(
            PlatformProviderConnectionRepository connections,
            PlatformProviderModelDiscoveryLookup discoveries,
            Clock clock) {
        this.connections = Objects.requireNonNull(connections, "平台供应商连接仓储不能为空");
        this.discoveries = Objects.requireNonNull(discoveries, "平台模型发现读取端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 只列出已验证连接指定修订的未过期候选。
     *
     * @throws ModelCatalogPermissionDeniedException 当前会话没有 `model-platform:write`。
     */
    public PageResult<PlatformProviderModelCandidateView> list(ListPlatformProviderModelCandidatesQuery query) {
        query = Objects.requireNonNull(query, "平台模型候选查询不能为空");
        requirePermission(query.context());
        var connection = connections.findById(PlatformProviderConnectionId.parse(query.providerConnectionId()))
                .orElseThrow(() -> new IllegalArgumentException("平台供应商连接不存在"));
        if (connection.version() != query.expectedConnectionVersion()
                || connection.status() != ProviderConnectionStatus.VERIFIED) {
            throw new IllegalStateException("平台供应商连接不可用");
        }
        var candidates = discoveries.findCurrentCandidates(connection.id(), connection.version(), query.page(), clock.instant(), query.q());
        return PageResult.of(candidates.items().stream()
                        .map(candidate -> new PlatformProviderModelCandidateView(
                                candidate.modelCode(),
                                candidate.modelType() == null ? null : candidate.modelType().name(),
                                candidate.capabilities().stream().map(Enum::name)
                                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                                candidate.classification().name(), candidate.displayName()))
                        .toList(),
                query.page(), candidates.totalElements());
    }

    private static void requirePermission(PlatformRequestContext context) {
        if (!Objects.requireNonNull(context, "平台请求上下文不能为空")
                .hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }
}
