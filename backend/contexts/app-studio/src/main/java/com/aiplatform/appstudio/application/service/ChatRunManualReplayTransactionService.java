package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.command.ReplayChatRunDeadLetterCommand;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ManualReplayRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaim;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaimState;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic dead-letter claim plus audit boundary; completion runs in a separate fenced transaction. */
@Service
public class ChatRunManualReplayTransactionService {
    private static final Duration REPLAY_LEASE = Duration.ofSeconds(30);

    private final ChatRunRepository repository;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    public ChatRunManualReplayTransactionService(
            ChatRunRepository repository,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "聊天运行仓储不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Transactional
    public ReplayClaim claim(ReplayChatRunDeadLetterCommand command) {
        Objects.requireNonNull(command, "聊天死信重放命令不能为空");
        Instant now = Instant.now(clock);
        ReplayClaim claim = repository.claimDeadLetterForReplay(new ManualReplayRequest(
                command.tenantId(),
                command.runId(),
                command.actorId(),
                command.replayRequestId(),
                command.reason(),
                UUID.randomUUID().toString(),
                now,
                now.plus(REPLAY_LEASE)
        ));
        if (claim.state() == ReplayClaimState.ACQUIRED) {
            auditPublisher.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    command.tenantId().value(),
                    command.actorId(),
                    "APP_CHAT_RUN",
                    command.runId(),
                    "APP_CHAT_RUN_DEAD_LETTER_REPLAY",
                    "HIGH",
                    null,
                    command.traceId(),
                    now,
                    Map.of(
                            "replayRequestId", command.replayRequestId(),
                            "reason", command.reason(),
                            "applicationId", claim.ownership().applicationId(),
                            "sessionId", claim.ownership().sessionId()
                    )
            ));
        }
        return claim;
    }
}
