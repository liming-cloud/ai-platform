package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.CreateTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.CreateTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.query.ListTextDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.PublishTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.PublishTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.TextDataSourceView;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.UploadTextContentCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.UploadTextContentResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.InvalidTextContentException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentProcessingException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextContentAttachment;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceCreation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourcePublication;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextDataSource;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户和权限边界内编排文本数据源。
 */
public final class TextDataSourceService {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final TextDataSourceRepository repository;
    private final TextContentStagingPort staging;
    private final TextContentObjectStoragePort objectStorage;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;

    public TextDataSourceService(
            TextDataSourceRepository repository,
            TextContentStagingPort staging,
            TextContentObjectStoragePort objectStorage,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit) {
        this.repository = Objects.requireNonNull(repository, "文本数据源仓储不能为空");
        this.staging = Objects.requireNonNull(staging, "文本内容暂存端口不能为空");
        this.objectStorage = Objects.requireNonNull(
                objectStorage,
                "文本对象存储端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户数据源配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
    }

    public CreateTextDataSourceResult create(CreateTextDataSourceCommand command) {
        Objects.requireNonNull(command, "创建数据源命令不能为空");
        requirePermission(command.context(), WRITE);
        var context = command.context();
        var knowledgeSpaceId = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var source = TextDataSource.create(
                context.tenantId(),
                knowledgeSpaceId,
                new DataSourceId(identifiers.nextId()),
                command.name(),
                new CreatorId(context.membershipId().value()),
                clock.instant());
        var outcome = repository.create(
                new TextDataSourceCreation(
                        source,
                        command.idempotencyKey(),
                        fingerprint(
                                "CREATE",
                                Long.toString(knowledgeSpaceId.value()),
                                source.name())),
                tenantLimit);
        return new CreateTextDataSourceResult(
                toView(outcome.source()),
                outcome.replayed());
    }

    public PageResult<TextDataSourceView> list(ListTextDataSourcesQuery query) {
        Objects.requireNonNull(query, "数据源列表查询不能为空");
        requirePermission(query.context(), READ);
        var page = repository.findPage(
                query.context().tenantId(),
                KnowledgeSpaceId.parse(query.knowledgeSpaceId()),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                page.items().stream()
                        .map(TextDataSourceService::toView)
                        .toList(),
                query.page(),
                page.totalElements());
    }

    public UploadTextContentResult upload(UploadTextContentCommand command) {
        Objects.requireNonNull(command, "文本内容上传命令不能为空");
        requirePermission(command.context(), WRITE);
        if (!TextContentReference.UTF8_TEXT.equals(command.mediaType())) {
            throw new InvalidTextContentException("只支持 UTF-8 纯文本");
        }
        var context = command.context();
        var knowledgeSpaceId = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var dataSourceId = DataSourceId.parse(command.dataSourceId());
        try (var staged = staging.stage(command.content())) {
            var requestFingerprint = fingerprint(
                    "ATTACH_CONTENT",
                    Long.toString(knowledgeSpaceId.value()),
                    Long.toString(dataSourceId.value()),
                    Long.toString(command.expectedVersion()),
                    staged.sha256());
            var replay = repository.findAttachmentReplay(
                    context.tenantId(),
                    command.idempotencyKey(),
                    requestFingerprint);
            if (replay.isPresent()) {
                return new UploadTextContentResult(
                        toView(replay.orElseThrow().source()),
                        true);
            }

            var current = repository.findById(
                    context.tenantId(),
                    knowledgeSpaceId,
                    dataSourceId);
            if (current.version() != command.expectedVersion()) {
                throw new DataSourceConcurrentModificationException();
            }
            var stored = objectStorage.put(new TextContentStorageRequest(
                    context.tenantId(),
                    knowledgeSpaceId,
                    dataSourceId,
                    current.contentVersion() + 1,
                    staged,
                    clock.instant()));
            requireStoredContent(
                    current.contentVersion() + 1,
                    staged.sha256(),
                    staged.byteSize(),
                    stored);
            var outcome = repository.attach(new TextContentAttachment(
                    context.tenantId(),
                    knowledgeSpaceId,
                    dataSourceId,
                    command.expectedVersion(),
                    stored,
                    command.idempotencyKey(),
                    requestFingerprint));
            return new UploadTextContentResult(
                    toView(outcome.source()),
                    outcome.replayed());
        } catch (IOException exception) {
            throw new TextContentProcessingException(exception);
        }
    }

    public PublishTextDataSourceResult publish(PublishTextDataSourceCommand command) {
        Objects.requireNonNull(command, "数据源发布命令不能为空");
        requirePermission(command.context(), WRITE);
        var context = command.context();
        var knowledgeSpaceId = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var dataSourceId = DataSourceId.parse(command.dataSourceId());
        var requestFingerprint = fingerprint(
                "PUBLISH",
                Long.toString(knowledgeSpaceId.value()),
                Long.toString(dataSourceId.value()),
                Long.toString(command.expectedVersion()));
        var replay = repository.findPublicationReplay(
                context.tenantId(),
                command.idempotencyKey(),
                requestFingerprint);
        if (replay.isPresent()) {
            return new PublishTextDataSourceResult(
                    toView(replay.orElseThrow().source()),
                    true);
        }
        var current = repository.findById(
                context.tenantId(),
                knowledgeSpaceId,
                dataSourceId);
        if (current.currentContent() == null) {
            throw new DataSourceStateException("数据源没有可发布的内容");
        }
        requireStoredMetadata(
                current.currentContent(),
                objectStorage.metadata(current.currentContent()));
        var outcome = repository.publish(new TextDataSourcePublication(
                context.tenantId(),
                knowledgeSpaceId,
                dataSourceId,
                command.expectedVersion(),
                command.idempotencyKey(),
                requestFingerprint,
                clock.instant()));
        return new PublishTextDataSourceResult(
                toView(outcome.source()),
                outcome.replayed());
    }

    private static void requireStoredContent(
            int expectedVersion,
            String expectedSha256,
            long expectedByteSize,
            TextContentReference stored) {
        if (expectedVersion != stored.version()
                || !expectedSha256.equals(stored.sha256())
                || expectedByteSize != stored.byteSize()
                || !TextContentReference.UTF8_TEXT.equals(stored.mediaType())) {
            throw new TextContentIntegrityException();
        }
    }

    private static void requireStoredMetadata(
            TextContentReference reference,
            TextContentObjectMetadata metadata) {
        if (!reference.objectKey().equals(metadata.objectKey())
                || !reference.sha256().equals(metadata.sha256())
                || reference.byteSize() != metadata.byteSize()
                || !reference.mediaType().equals(metadata.mediaType())) {
            throw new TextContentIntegrityException();
        }
    }

    private static void requirePermission(
            TrustedRequestContext context,
            PermissionCode permission) {
        if (!context.hasPermission(permission)) {
            throw new KnowledgePermissionDeniedException();
        }
    }

    private static TextDataSourceView toView(TextDataSource source) {
        return new TextDataSourceView(
                source.id().toString(),
                source.knowledgeSpaceId().toString(),
                source.name(),
                source.status().name(),
                source.version(),
                source.contentVersion(),
                source.publishedContentVersion(),
                source.createdAt());
    }

    private static TextDataSourceView toView(TextDataSourceSummary source) {
        return new TextDataSourceView(
                source.id().toString(),
                source.knowledgeSpaceId().toString(),
                source.name(),
                source.status().name(),
                source.version(),
                source.contentVersion(),
                source.publishedContentVersion(),
                source.createdAt());
    }

    private static String fingerprint(String... values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var value : values) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
