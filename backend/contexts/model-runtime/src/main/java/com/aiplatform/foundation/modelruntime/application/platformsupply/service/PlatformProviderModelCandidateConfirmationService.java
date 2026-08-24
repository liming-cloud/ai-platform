package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ConfirmPlatformProviderModelCandidateCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelCandidateConfirmationStore;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import java.time.Clock;
import java.util.Objects;
import java.util.stream.Collectors;

/** 校验平台连接修订并固化管理员的模型类型确认。 */
public final class PlatformProviderModelCandidateConfirmationService {
    private final PlatformProviderConnectionRepository connections;
    private final PlatformProviderModelCandidateConfirmationStore confirmations;
    private final Clock clock;

    public PlatformProviderModelCandidateConfirmationService(
            PlatformProviderConnectionRepository connections,
            PlatformProviderModelCandidateConfirmationStore confirmations,
            Clock clock) {
        this.connections = Objects.requireNonNull(connections, "平台供应商连接仓储不能为空");
        this.confirmations = Objects.requireNonNull(confirmations, "平台模型候选确认存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /** 只有平台模型管理员可确认当前已验证连接的待确认候选。 */
    public PlatformProviderModelCandidateView confirm(
            ConfirmPlatformProviderModelCandidateCommand command) {
        command = Objects.requireNonNull(command, "平台模型候选确认命令不能为空");
        if (!command.context().hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
        var connection = connections.findById(PlatformProviderConnectionId.parse(command.providerConnectionId()))
                .orElseThrow(() -> new IllegalArgumentException("平台供应商连接不存在"));
        if (connection.version() != command.expectedConnectionVersion()
                || connection.status() != ProviderConnectionStatus.VERIFIED) {
            throw new IllegalStateException("平台供应商连接不可用");
        }
        var now = clock.instant();
        var candidate = confirmations.confirmCurrentCandidate(
                connection.id(), connection.version(), command.modelCode(), command.modelType(),
                command.context().subjectId().value(), now, now);
        return new PlatformProviderModelCandidateView(
                candidate.modelCode(), candidate.modelType().name(),
                candidate.capabilities().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                candidate.classification().name());
    }
}
