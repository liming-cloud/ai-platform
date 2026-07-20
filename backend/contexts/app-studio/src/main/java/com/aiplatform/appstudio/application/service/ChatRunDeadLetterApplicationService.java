package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.command.ReplayChatRunDeadLetterCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaim;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaimState;
import com.aiplatform.appstudio.application.result.ChatRunDeadLetterResult;
import com.aiplatform.appstudio.application.result.ChatRunManualReplayResult;
import com.aiplatform.appstudio.presentation.permission.AppStudioPermissionCatalog;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Privileged operations for querying and replaying tenant-owned chat finalization dead letters. */
@Service
public class ChatRunDeadLetterApplicationService {
    private static final int MIN_QUERY_LIMIT = 1;
    private static final int MAX_QUERY_LIMIT = 100;
    private final ChatRunRepository repository;
    private final ChatRunManualReplayTransactionService manualReplayTransactions;
    private final ChatRunTransactionService finalizationTransactions;
    private final RbacAuthorizationQueryPort authorization;

    public ChatRunDeadLetterApplicationService(
            ChatRunRepository repository,
            ChatRunManualReplayTransactionService manualReplayTransactions,
            ChatRunTransactionService finalizationTransactions,
            RbacAuthorizationQueryPort authorization
    ) {
        this.repository = Objects.requireNonNull(repository, "聊天运行仓储不能为空");
        this.manualReplayTransactions = Objects.requireNonNull(manualReplayTransactions, "人工重放事务服务不能为空");
        this.finalizationTransactions = Objects.requireNonNull(finalizationTransactions, "最终化事务服务不能为空");
        this.authorization = Objects.requireNonNull(authorization, "RBAC 授权查询端口不能为空");
    }

    public List<ChatRunDeadLetterResult> listDeadLetters(TenantId tenantId, String actorId, int limit) {
        int boundedLimit = requireQueryLimit(limit);
        requireAdministrator(tenantId, actorId);
        return repository.findDeadLetters(tenantId, boundedLimit).stream()
                .map(ChatRunDeadLetterResult::from)
                .toList();
    }

    public ChatRunManualReplayResult replay(ReplayChatRunDeadLetterCommand command) {
        Objects.requireNonNull(command, "聊天死信重放命令不能为空");
        requireAdministrator(command.tenantId(), command.actorId());
        ReplayClaim claim = manualReplayTransactions.claim(command);
        if (claim.state() == ReplayClaimState.ALREADY_ACCEPTED) {
            return new ChatRunManualReplayResult(command.runId(), command.replayRequestId(), claim.persistedStatus());
        }
        try {
            finalizationTransactions.complete(claim.ownership(), claim.finalization());
            return new ChatRunManualReplayResult(command.runId(), command.replayRequestId(), "COMPLETED");
        } catch (RuntimeException exception) {
            String status = finalizationTransactions.recordFinalizationFailure(
                    claim.ownership(), command.traceId()
            ).name();
            return new ChatRunManualReplayResult(command.runId(), command.replayRequestId(), status);
        }
    }

    private void requireAdministrator(TenantId tenantId, String actorId) {
        TenantId requiredTenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        String requiredActorId = requireText(actorId, "操作者 ID");
        Set<PermissionCode> permissions = authorization.findPermissionCodes(
                requiredTenantId, "user", requiredActorId
        );
        if (!permissions.contains(AppStudioPermissionCatalog.APPLICATION_PUBLISH)
                || !permissions.contains(AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL)) {
            throw new ApplicationException(
                    AppStudioErrorCodes.AUTHORIZATION_DENIED,
                    "当前主体无权查询或重放聊天最终化死信"
            );
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static int requireQueryLimit(int limit) {
        if (limit < MIN_QUERY_LIMIT || limit > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("聊天最终化死信查询条数必须在 1 到 100 之间");
        }
        return limit;
    }
}
