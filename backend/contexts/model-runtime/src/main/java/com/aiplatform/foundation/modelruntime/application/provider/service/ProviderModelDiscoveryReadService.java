package com.aiplatform.foundation.modelruntime.application.provider.service;

import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryHistoryItem;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryView;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryHistoryReader;
import com.aiplatform.foundation.modelruntime.application.provider.query.ListProviderModelDiscoveriesQuery;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** 在可信租户范围分页读取模型发现安全摘要。 */
public final class ProviderModelDiscoveryReadService {

    private static final PermissionCode READ = new PermissionCode("model:read");

    private final ProviderModelDiscoveryHistoryReader reader;
    private final Clock clock;

    public ProviderModelDiscoveryReadService(
            ProviderModelDiscoveryHistoryReader reader,
            Clock clock) {
        this.reader = Objects.requireNonNull(reader, "模型发现历史读取端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 返回当前租户指定连接的服务端分页发现摘要。
     *
     * @throws ModelCatalogPermissionDeniedException 当前成员没有 `model:read` 时抛出。
     */
    public PageResult<ProviderModelDiscoveryView> list(
            ListProviderModelDiscoveriesQuery query) {
        Objects.requireNonNull(query, "模型发现历史查询不能为空");
        if (!query.context().hasPermission(READ)) {
            throw new ModelCatalogPermissionDeniedException();
        }
        var page = reader.findPage(
                query.context().tenantId(),
                ProviderConnectionId.parse(query.providerConnectionId()),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                page.items().stream().map(this::toView).toList(),
                query.page(),
                page.totalElements());
    }

    private ProviderModelDiscoveryView toView(ProviderModelDiscoveryHistoryItem item) {
        var nextStep = item.succeeded() && item.expiresAt().isAfter(clock.instant())
                ? "CREATE_DEPLOYMENT"
                : "DISCOVER_MODELS";
        return new ProviderModelDiscoveryView(
                item.discoveryId(),
                item.providerConnectionRevision(),
                item.succeeded(),
                item.candidateCount(),
                item.resultCode(),
                Duration.ofMillis(item.elapsedMillis()),
                item.discoveredAt(),
                item.expiresAt(),
                nextStep);
    }
}
