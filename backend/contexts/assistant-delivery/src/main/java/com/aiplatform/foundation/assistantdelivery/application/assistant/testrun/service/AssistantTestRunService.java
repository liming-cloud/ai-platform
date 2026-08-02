package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.service;

import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerTarget;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutionException;
import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutor;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunTerminalCommit;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.port.out.AssistantTestRunTerminalCommitter;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.DraftAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.command.RunAssistantDraftTestCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestCitationResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestCitation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRefusalReason;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRequesterId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRun;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestTarget;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestUsage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository.AssistantTestRunRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 编排草稿助手测试的独立配额、可信执行和最小终态提交。
 */
public final class AssistantTestRunService {

    private static final PermissionCode KNOWLEDGE_READ = new PermissionCode("knowledge:read");

    private final DraftAssistantVersionLookup drafts;
    private final AssistantTestRunRepository repository;
    private final AssistantTestRunTerminalCommitter terminalCommitter;
    private final AssistantTestQuotaPolicy quota;
    private final AssistantTrustedAnswerExecutor executor;
    private final Clock clock;
    private final EntityIdGenerator identifiers;

    public AssistantTestRunService(
            DraftAssistantVersionLookup drafts,
            AssistantTestRunRepository repository,
            AssistantTestRunTerminalCommitter terminalCommitter,
            AssistantTestQuotaPolicy quota,
            AssistantTrustedAnswerExecutor executor,
            Clock clock,
            EntityIdGenerator identifiers) {
        this.drafts = Objects.requireNonNull(drafts, "草稿助手版本查询不能为空");
        this.repository = Objects.requireNonNull(repository, "助手测试运行仓储不能为空");
        this.terminalCommitter = Objects.requireNonNull(terminalCommitter, "助手测试终态提交器不能为空");
        this.quota = Objects.requireNonNull(quota, "助手测试配额策略不能为空");
        this.executor = Objects.requireNonNull(executor, "可信回答执行门面不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    /**
     * 在独立测试配额已原子占用后执行草稿可信回答，并只提交最小终态事实。
     */
    public AssistantTestRunResult run(RunAssistantDraftTestCommand command) {
        Objects.requireNonNull(command, "草稿助手测试命令不能为空");
        if (!command.context().hasPermission(KNOWLEDGE_READ)) {
            throw new AssistantPermissionDeniedException();
        }
        var draft = drafts.find(command.context(), parsePositive(command.assistantVersionId()))
                .orElseThrow(IllegalArgumentException::new);
        var started = AssistantTestRun.start(
                command.context().tenantId(), new AssistantTestRunId(identifiers.nextId()),
                new AssistantTestRequesterId(command.context().membershipId().value()), toTarget(draft),
                digest(command.question()), clock.instant());
        var claimed = repository.claim(started, quota);
        final com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerResult answer;
        try {
            answer = executor.execute(
                    command.context(), AssistantTrustedAnswerTarget.from(draft), command.question());
        } catch (AssistantTrustedAnswerExecutionException exception) {
            commit(claimed.fail(clock.instant()), claimed.version(), exception.observation().modelInvocationTrace());
            throw exception;
        } catch (RuntimeException exception) {
            commit(claimed.fail(clock.instant()), claimed.version(), null);
            throw exception;
        }
        if (answer.outcome() == com.aiplatform.foundation.assistantdelivery.application.answer.dto
                .AssistantTrustedAnswerResult.Outcome.REFUSED_INSUFFICIENT_EVIDENCE) {
            var refused = claimed.refuse(AssistantTestRefusalReason.INSUFFICIENT_EVIDENCE, clock.instant());
            commit(refused, claimed.version(), null);
            return new AssistantTestRunResult(Long.toString(refused.id().value()), refused.status(), null, java.util.List.of());
        }
        var completed = claimed.answer(
                new AssistantTestUsage(
                        Math.toIntExact(answer.usage().inputTokens()),
                        Math.toIntExact(answer.usage().outputTokens())),
                answer.citations().stream().map(value -> new AssistantTestCitation(
                        value.sequence(), value.dataSourceId(), value.contentVersion(),
                        value.chunkId(), value.ordinal())).toList(),
                clock.instant());
        commit(completed, claimed.version(), answer.modelInvocationTrace());
        return new AssistantTestRunResult(
                Long.toString(completed.id().value()), completed.status(), answer.answer(),
                answer.citations().stream().map(value -> new AssistantTestCitationResult(
                        value.sequence(), Long.toString(value.dataSourceId()), value.contentVersion(),
                        value.chunkId(), value.ordinal(), value.quote(), value.score())).toList());
    }

    private void commit(
            AssistantTestRun run,
            long expectedVersion,
            com.aiplatform.foundation.assistantdelivery.application.answer.dto.ModelInvocationTrace trace) {
        terminalCommitter.commit(new AssistantTestRunTerminalCommit(run, expectedVersion, trace));
    }

    private static AssistantTestTarget toTarget(
            com.aiplatform.foundation.assistantdelivery.application.assistant.dto.DraftAssistantVersionSnapshot value) {
        return new AssistantTestTarget(
                value.assistantVersionId(), value.knowledgeSpaceId(), value.indexVersionId(),
                value.indexVersionRevision(), value.governedChat(), value.governedEmbedding(),
                value.systemInstruction(), value.maxOutputTokens(), value.retrievalLimit(),
                value.minimumScore(), value.citationPolicy(), value.refusalPolicy());
    }

    private static long parsePositive(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("助手版本标识必须是正整数", exception);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
