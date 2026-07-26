package com.aiplatform.foundation.knowledgedata.application.datasource;

import com.aiplatform.foundation.knowledgedata.api.TextDataSourceUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.CreateTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.CreateTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.api.model.ListTextDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.api.model.PublishTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.PublishTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.api.model.TextDataSourcePage;
import com.aiplatform.foundation.knowledgedata.api.model.TextDataSourceView;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourcePublication;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextDataSource;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 在可信租户和权限边界内编排文本数据源。
 */
public final class DefaultTextDataSourceService implements TextDataSourceUseCase {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final TextDataSourceRepository repository;
    private final Clock clock;
    private final Supplier<UUID> identifiers;
    private final int tenantLimit;

    public DefaultTextDataSourceService(
            TextDataSourceRepository repository,
            Clock clock,
            Supplier<UUID> identifiers,
            int tenantLimit) {
        this.repository = Objects.requireNonNull(repository, "文本数据源仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户数据源配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
    }

    @Override
    public CreateTextDataSourceResult create(CreateTextDataSourceCommand command) {
        Objects.requireNonNull(command, "创建数据源命令不能为空");
        requirePermission(command.context(), WRITE);
        var context = command.context();
        var knowledgeSpaceId = new KnowledgeSpaceId(command.knowledgeSpaceId());
        var source = TextDataSource.create(
                context.tenantId(),
                knowledgeSpaceId,
                new DataSourceId(identifiers.get()),
                command.name(),
                context.membershipId(),
                clock.instant());
        var outcome = repository.create(
                new TextDataSourceCreation(
                        source,
                        command.idempotencyKey(),
                        fingerprint(
                                "CREATE",
                                knowledgeSpaceId.value().toString(),
                                source.name())),
                tenantLimit);
        return new CreateTextDataSourceResult(
                toView(outcome.source()),
                outcome.replayed());
    }

    @Override
    public TextDataSourcePage list(ListTextDataSourcesQuery query) {
        Objects.requireNonNull(query, "数据源列表查询不能为空");
        requirePermission(query.context(), READ);
        var slice = repository.findPage(
                query.context().tenantId(),
                new KnowledgeSpaceId(query.knowledgeSpaceId()),
                query.cursor(),
                query.limit());
        return new TextDataSourcePage(
                slice.items().stream()
                        .map(DefaultTextDataSourceService::toView)
                        .toList(),
                slice.nextCursor());
    }

    @Override
    public PublishTextDataSourceResult publish(PublishTextDataSourceCommand command) {
        Objects.requireNonNull(command, "数据源发布命令不能为空");
        requirePermission(command.context(), WRITE);
        var context = command.context();
        var knowledgeSpaceId = new KnowledgeSpaceId(command.knowledgeSpaceId());
        var dataSourceId = new DataSourceId(command.dataSourceId());
        var outcome = repository.publish(new TextDataSourcePublication(
                context.tenantId(),
                knowledgeSpaceId,
                dataSourceId,
                command.expectedVersion(),
                command.idempotencyKey(),
                fingerprint(
                        "PUBLISH",
                        knowledgeSpaceId.value().toString(),
                        dataSourceId.value().toString(),
                        Long.toString(command.expectedVersion())),
                clock.instant()));
        return new PublishTextDataSourceResult(
                toView(outcome.source()),
                outcome.replayed());
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
                source.id().value(),
                source.knowledgeSpaceId().value(),
                source.name(),
                source.status().name(),
                source.version(),
                source.contentVersion(),
                source.publishedContentVersion(),
                source.createdAt());
    }

    private static TextDataSourceView toView(TextDataSourceSummary source) {
        return new TextDataSourceView(
                source.id().value(),
                source.knowledgeSpaceId().value(),
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
