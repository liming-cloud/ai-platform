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
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
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
    private final UsableModelConfigurationLookup modelConfigurations;
    private final ActiveIndexLookup activeIndexes;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;
    private final int versionLimit;

    public AssistantService(
            AssistantRepository repository,
            UsableModelConfigurationLookup modelConfigurations,
            ActiveIndexLookup activeIndexes,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit,
            int versionLimit) {
        this.repository = Objects.requireNonNull(repository, "助手仓储不能为空");
        this.modelConfigurations = modelConfigurations;
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
        var chat = requireModelConfigurations()
                .find(
                        command.context(),
                        new ModelConfigurationId(parsePositiveIdentifier(
                                command.chatModelConfigurationId(), "对话模型配置标识")),
                        ModelType.CHAT)
                .orElseThrow(AssistantReferenceUnavailableException::new);
        var embedding = requireModelConfigurations()
                .find(
                        command.context(),
                        new ModelConfigurationId(parsePositiveIdentifier(
                                command.embeddingModelConfigurationId(), "嵌入模型配置标识")),
                        ModelType.EMBEDDING)
                .orElseThrow(AssistantReferenceUnavailableException::new);
        var index = activeIndexes
                .find(
                        command.context().tenantId(),
                        parsePositiveIdentifier(
                                command.knowledgeSpaceId(),
                                "知识空间标识"))
                .orElseThrow(AssistantReferenceUnavailableException::new);
        var governedEmbedding = governedEmbedding(embedding);
        if (!index.embedding().isGoverned()
                || !matches(index.embedding(), governedEmbedding)) {
            throw new AssistantReferenceUnavailableException();
        }
        var advanced = assistant.advanceVersion();
        var version = AssistantVersion.governedDraft(
                command.context().tenantId(),
                new AssistantVersionId(identifiers.nextId()),
                assistant.id(),
                advanced.latestVersionNumber(),
                governedChat(chat),
                index.knowledgeSpaceId(),
                index.indexVersionId(),
                index.indexVersionRevision(),
                governedEmbedding,
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
        requirePublishableBindings(command.context(), current);
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
                version.usesGovernedModelConfigurations()
                        ? null : Long.toString(version.chatDeploymentId()),
                version.usesGovernedModelConfigurations()
                        ? 0 : version.chatDeploymentRevision(),
                nullableId(version.governedChat() == null
                        ? null : version.governedChat().configurationId()),
                nullableRevision(version.governedChat() == null
                        ? null : version.governedChat().configurationRevision()),
                nullableId(version.governedChat() == null
                        ? null : version.governedChat().catalogEntryId()),
                nullableRevision(version.governedChat() == null
                        ? null : version.governedChat().catalogEntryRevision()),
                Long.toString(version.knowledgeSpaceId()),
                Long.toString(version.indexVersionId()),
                version.indexVersionRevision(),
                version.usesGovernedModelConfigurations()
                        ? null : Long.toString(version.embeddingDeploymentId()),
                version.usesGovernedModelConfigurations()
                        ? 0 : version.embeddingDeploymentRevision(),
                nullableId(version.governedEmbedding() == null
                        ? null : version.governedEmbedding().configurationId()),
                nullableRevision(version.governedEmbedding() == null
                        ? null : version.governedEmbedding().configurationRevision()),
                nullableId(version.governedEmbedding() == null
                        ? null : version.governedEmbedding().catalogEntryId()),
                nullableRevision(version.governedEmbedding() == null
                        ? null : version.governedEmbedding().catalogEntryRevision()),
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
        update(digest, command.chatModelConfigurationId());
        update(digest, command.embeddingModelConfigurationId());
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

    private void requirePublishableBindings(
            TrustedRequestContext context,
            AssistantVersion version) {
        if (!version.usesGovernedModelConfigurations()) {
            return;
        }
        var chat = requireModelConfigurations().find(
                context,
                new ModelConfigurationId(version.governedChat().configurationId()),
                ModelType.CHAT).orElseThrow(AssistantReferenceUnavailableException::new);
        var embedding = requireModelConfigurations().find(
                context,
                new ModelConfigurationId(version.governedEmbedding().configurationId()),
                ModelType.EMBEDDING).orElseThrow(AssistantReferenceUnavailableException::new);
        var index = activeIndexes.find(context.tenantId(), version.knowledgeSpaceId())
                .orElseThrow(AssistantReferenceUnavailableException::new);
        if (!governedChat(chat).equals(version.governedChat())
                || !governedEmbedding(embedding).equals(version.governedEmbedding())
                || index.indexVersionId() != version.indexVersionId()
                || index.indexVersionRevision() != version.indexVersionRevision()
                || !index.embedding().isGoverned()
                || !matches(index.embedding(), version.governedEmbedding())) {
            throw new AssistantReferenceUnavailableException();
        }
    }

    private static GovernedChatModelBinding governedChat(
            com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot snapshot) {
        snapshot.capabilities().requireFor(ModelType.CHAT);
        return new GovernedChatModelBinding(
                snapshot.configurationId().value(), snapshot.configurationRevision(),
                snapshot.catalogEntryId().value(), snapshot.catalogEntryRevision(),
                snapshot.capabilities().outputLimit());
    }

    private UsableModelConfigurationLookup requireModelConfigurations() {
        if (modelConfigurations == null) {
            throw new AssistantReferenceUnavailableException();
        }
        return modelConfigurations;
    }

    private static GovernedEmbeddingModelBinding governedEmbedding(
            com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot snapshot) {
        snapshot.capabilities().requireFor(ModelType.EMBEDDING);
        return new GovernedEmbeddingModelBinding(
                snapshot.configurationId().value(), snapshot.configurationRevision(),
                snapshot.catalogEntryId().value(), snapshot.catalogEntryRevision(),
                snapshot.capabilities().embeddingDimension());
    }

    private static boolean matches(
            com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration index,
            GovernedEmbeddingModelBinding embedding) {
        return index.configurationId() == embedding.configurationId()
                && index.configurationRevision() == embedding.configurationRevision()
                && index.catalogEntryId() == embedding.catalogEntryId()
                && index.catalogEntryRevision() == embedding.catalogEntryRevision()
                && index.dimension() == embedding.dimension();
    }

    private static String nullableId(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static long nullableRevision(Long value) {
        return value == null ? 0 : value;
    }
}
