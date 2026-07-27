package com.aiplatform.foundation.assistantdelivery.application.assistant.service;

import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.AssistantVersionView;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.AssistantView;
import com.aiplatform.foundation.assistantdelivery.application.assistant.command.CreateAssistantCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.CreateAssistantResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.command.CreateAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.CreateAssistantVersionResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.query.ListAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.application.assistant.command.PublishAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.PublishAssistantVersionResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantConcurrentModificationException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantNotFoundException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantReferenceUnavailableException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantVersionNotFoundException;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户边界内创建助手并冻结模型、索引与回答策略。
 */
public final class AssistantService {

    private static final PermissionCode READ = new PermissionCode("assistant:read");
    private static final PermissionCode WRITE = new PermissionCode("assistant:write");

    private final AssistantRepository repository;
    private final PublishedChatDeploymentLookup chatDeployments;
    private final ActiveIndexLookup activeIndexes;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;
    private final int versionLimit;

    public AssistantService(
            AssistantRepository repository,
            PublishedChatDeploymentLookup chatDeployments,
            ActiveIndexLookup activeIndexes,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit,
            int versionLimit) {
        this.repository = Objects.requireNonNull(repository, "助手仓储不能为空");
        this.chatDeployments =
                Objects.requireNonNull(chatDeployments, "已发布对话部署查询不能为空");
        this.activeIndexes = Objects.requireNonNull(activeIndexes, "活动索引查询不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1 || versionLimit < 1) {
            throw new IllegalArgumentException("助手和版本配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
        this.versionLimit = versionLimit;
    }

    public CreateAssistantResult create(CreateAssistantCommand command) {
        Objects.requireNonNull(command, "创建助手命令不能为空");
        requirePermission(command.context(), WRITE);
        var assistant = Assistant.create(
                command.context().tenantId(),
                new AssistantId(identifiers.nextId()),
                command.displayName(),
                new CreatorId(command.context().membershipId().value()),
                clock.instant());
        var outcome = repository.create(
                new AssistantCreation(
                        assistant,
                        command.idempotencyKey(),
                        fingerprint(command.displayName())),
                tenantLimit);
        return new CreateAssistantResult(toView(outcome.assistant()), outcome.replayed());
    }

    public PageResult<AssistantView> list(ListAssistantsQuery query) {
        Objects.requireNonNull(query, "助手列表查询不能为空");
        requirePermission(query.context(), READ);
        var page = repository.findPage(
                query.context().tenantId(),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                page.items().stream()
                        .map(AssistantService::toView)
                        .toList(),
                query.page(),
                page.totalElements());
    }

    public CreateAssistantVersionResult createVersion(
            CreateAssistantVersionCommand command) {
        Objects.requireNonNull(command, "创建助手版本命令不能为空");
        requirePermission(command.context(), WRITE);
        var assistant = repository
                .findAssistant(
                        command.context().tenantId(),
                        AssistantId.parse(command.assistantId()))
                .orElseThrow(AssistantNotFoundException::new);
        var chat = chatDeployments
                .find(
                        command.context().tenantId(),
                        parseDeploymentId(command.chatDeploymentId()))
                .orElseThrow(AssistantReferenceUnavailableException::new);
        var index = activeIndexes
                .find(
                        command.context().tenantId(),
                        parsePositiveIdentifier(
                                command.knowledgeSpaceId(),
                                "知识空间标识"))
                .orElseThrow(AssistantReferenceUnavailableException::new);
        var advanced = assistant.advanceVersion();
        var version = AssistantVersion.draft(
                command.context().tenantId(),
                new AssistantVersionId(identifiers.nextId()),
                assistant.id(),
                advanced.latestVersionNumber(),
                chat.deploymentId(),
                chat.deploymentRevision(),
                chat.outputLimit(),
                index.knowledgeSpaceId(),
                index.indexVersionId(),
                index.indexVersionRevision(),
                index.embeddingDeploymentId(),
                index.embeddingDeploymentRevision(),
                index.embeddingDimension(),
                command.systemInstruction(),
                command.maxOutputTokens(),
                command.retrievalLimit(),
                command.minimumScore(),
                command.citationPolicy(),
                command.refusalPolicy(),
                new CreatorId(command.context().membershipId().value()),
                clock.instant());
        var outcome = repository.createVersion(
                new AssistantVersionCreation(
                        assistant,
                        advanced,
                        version,
                        command.idempotencyKey(),
                        fingerprint(command)),
                versionLimit);
        return new CreateAssistantVersionResult(
                toView(outcome.assistantVersion()),
                outcome.replayed());
    }

    public PublishAssistantVersionResult publishVersion(
            PublishAssistantVersionCommand command) {
        Objects.requireNonNull(command, "发布助手版本命令不能为空");
        requirePermission(command.context(), WRITE);
        var versionId = AssistantVersionId.parse(command.assistantVersionId());
        var fingerprint = fingerprint(
                command.assistantVersionId(),
                command.expectedVersion());
        var replayed = repository.replayPublication(
                command.context().tenantId(),
                versionId,
                command.idempotencyKey(),
                fingerprint);
        if (replayed.isPresent()) {
            return new PublishAssistantVersionResult(
                    toView(replayed.orElseThrow().assistantVersion()),
                    true);
        }
        var current = repository
                .findVersion(
                        command.context().tenantId(),
                        versionId)
                .orElseThrow(AssistantVersionNotFoundException::new);
        if (current.version() != command.expectedVersion()) {
            throw new AssistantConcurrentModificationException();
        }
        var published = current.publish(clock.instant());
        var outcome = repository.publishVersion(new AssistantVersionPublication(
                published,
                command.expectedVersion(),
                command.idempotencyKey(),
                fingerprint));
        return new PublishAssistantVersionResult(
                toView(outcome.assistantVersion()),
                outcome.replayed());
    }

    private static void requirePermission(
            TrustedRequestContext context,
            PermissionCode permission) {
        if (!context.hasPermission(permission)) {
            throw new AssistantPermissionDeniedException();
        }
    }

    private static AssistantView toView(Assistant assistant) {
        return new AssistantView(
                assistant.id().toString(),
                assistant.displayName(),
                assistant.latestVersionNumber(),
                assistant.version(),
                assistant.createdAt());
    }

    static AssistantVersionView toView(AssistantVersion version) {
        return new AssistantVersionView(
                version.id().toString(),
                version.assistantId().toString(),
                version.number(),
                Long.toString(version.chatDeploymentId()),
                version.chatDeploymentRevision(),
                Long.toString(version.knowledgeSpaceId()),
                Long.toString(version.indexVersionId()),
                version.indexVersionRevision(),
                Long.toString(version.embeddingDeploymentId()),
                version.embeddingDeploymentRevision(),
                version.embeddingDimension(),
                version.systemInstruction(),
                version.maxOutputTokens(),
                version.retrievalLimit(),
                version.minimumScore(),
                version.citationPolicy(),
                version.refusalPolicy(),
                version.status().name(),
                version.version(),
                version.createdAt(),
                version.publishedAt());
    }

    private static String fingerprint(CreateAssistantVersionCommand command) {
        var digest = digest();
        update(digest, command.assistantId().toString());
        update(digest, command.chatDeploymentId().toString());
        update(digest, command.knowledgeSpaceId());
        update(digest, command.systemInstruction());
        update(digest, Integer.toString(command.maxOutputTokens()));
        update(digest, Integer.toString(command.retrievalLimit()));
        update(digest, Double.toString(command.minimumScore()));
        update(digest, command.citationPolicy().name());
        update(digest, command.refusalPolicy().name());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String fingerprint(String value) {
        var digest = digest();
        update(digest, value);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static long parsePositiveIdentifier(String value, String field) {
        Objects.requireNonNull(value, field + "不能为空");
        try {
            var identifier = Long.parseLong(value);
            if (identifier <= 0) {
                throw new IllegalArgumentException(field + "必须是正整数");
            }
            return identifier;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + "格式不正确", exception);
        }
    }

    private static String fingerprint(String assistantVersionId, long expectedVersion) {
        var digest = digest();
        update(digest, assistantVersionId.toString());
        update(digest, Long.toString(expectedVersion));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static long parseDeploymentId(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("模型部署标识必须是正整数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("模型部署标识格式不正确", exception);
        }
    }
}
