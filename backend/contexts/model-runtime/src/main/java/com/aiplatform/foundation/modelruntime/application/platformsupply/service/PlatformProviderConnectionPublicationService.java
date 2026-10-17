package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderConnectionPublicationView;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPublicationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Duration;
import java.util.Objects;

/** 独立连接发布用例；不创建模型部署、目录或租户授权。 */
public final class PlatformProviderConnectionPublicationService {
    private final PlatformProviderConnectionWritePolicy writes;
    private final PlatformProviderConnectionRepository connections;
    private final PlatformProviderConnectionPublicationRepository publications;
    private final EntityIdGenerator identifiers;
    private final Duration validity;

    public PlatformProviderConnectionPublicationService(PlatformProviderConnectionRepository connections,
            PlatformProviderConnectionPublicationRepository publications,
            EntityIdGenerator identifiers, Duration validity, PlatformProviderConnectionWritePolicy writes) {
        this.writes = Objects.requireNonNull(writes, "连接写入策略不能为空");
        this.connections = Objects.requireNonNull(connections, "连接仓储不能为空");
        this.publications = Objects.requireNonNull(publications, "发布仓储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.validity = Objects.requireNonNull(validity, "探测有效期不能为空");
        if (validity.isZero() || validity.isNegative()) throw new IllegalArgumentException("探测有效期必须大于零");
    }

    /** 先授权，仓储在连接锁内复验修订和时效；重复命令读取原发布事实。 */
    public PlatformProviderConnectionPublicationView publish(PublishPlatformProviderConnectionCommand command) {
        Objects.requireNonNull(command, "发布命令不能为空");
        requirePermission(command.context());
        writes.requireAvailable();
        return PlatformProviderConnectionPublicationView.from(publications.publish(
                PlatformProviderConnectionId.parse(command.providerConnectionId()), command.expectedVersion(),
                identifiers.nextId(), command.context().subjectId().value(), validity));
    }

    /** 精确读取当前修订的发布状态，旧连接版本不能冒充当前状态。 */
    public PlatformProviderConnectionPublicationView get(PlatformRequestContext context, String id, long expectedVersion) {
        requirePermission(context);
        var connectionId = PlatformProviderConnectionId.parse(id);
        var connection = connections.findById(connectionId).orElseThrow(ProviderConnectionNotFoundException::new);
        if (connection.version() != expectedVersion) throw new ProviderConnectionConcurrentModificationException();
        return publications.find(connectionId, expectedVersion).map(PlatformProviderConnectionPublicationView::from)
                .orElseGet(() -> PlatformProviderConnectionPublicationView.unpublished(id, expectedVersion));
    }

    private static void requirePermission(PlatformRequestContext context) {
        Objects.requireNonNull(context, "平台上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)) throw new ModelCatalogPermissionDeniedException();
    }
}
