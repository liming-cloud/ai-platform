package com.aiplatform.foundation.knowledgedata.application.knowledge;

import com.aiplatform.foundation.knowledgedata.api.KnowledgeSpaceUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.CreateKnowledgeSpaceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.CreateKnowledgeSpaceResult;
import com.aiplatform.foundation.knowledgedata.api.model.KnowledgeSpacePage;
import com.aiplatform.foundation.knowledgedata.api.model.KnowledgeSpaceView;
import com.aiplatform.foundation.knowledgedata.api.model.ListKnowledgeSpacesQuery;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
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
 * 在可信租户和权限边界内编排知识空间。
 */
public final class DefaultKnowledgeSpaceService implements KnowledgeSpaceUseCase {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final KnowledgeSpaceRepository repository;
    private final Clock clock;
    private final Supplier<UUID> identifiers;
    private final int tenantLimit;

    public DefaultKnowledgeSpaceService(
            KnowledgeSpaceRepository repository,
            Clock clock,
            Supplier<UUID> identifiers,
            int tenantLimit) {
        this.repository = Objects.requireNonNull(repository, "知识空间仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户知识空间配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
    }

    @Override
    public CreateKnowledgeSpaceResult create(CreateKnowledgeSpaceCommand command) {
        Objects.requireNonNull(command, "创建命令不能为空");
        requirePermission(command.context(), WRITE);
        var context = command.context();
        var space = KnowledgeSpace.create(
                context.tenantId(),
                new KnowledgeSpaceId(identifiers.get()),
                command.name(),
                command.description(),
                context.membershipId(),
                clock.instant());
        var outcome = repository.create(
                new KnowledgeSpaceCreation(
                        space,
                        command.idempotencyKey(),
                        fingerprint(space.name(), space.description())),
                tenantLimit);
        return new CreateKnowledgeSpaceResult(toView(outcome.space()), outcome.replayed());
    }

    @Override
    public KnowledgeSpacePage list(ListKnowledgeSpacesQuery query) {
        Objects.requireNonNull(query, "列表查询不能为空");
        requirePermission(query.context(), READ);
        var slice = repository.findPage(
                query.context().tenantId(),
                query.cursor(),
                query.limit());
        return new KnowledgeSpacePage(
                slice.items().stream().map(DefaultKnowledgeSpaceService::toView).toList(),
                slice.nextCursor());
    }

    private static void requirePermission(
            TrustedRequestContext context,
            PermissionCode permission) {
        if (!context.hasPermission(permission)) {
            throw new KnowledgePermissionDeniedException();
        }
    }

    private static KnowledgeSpaceView toView(KnowledgeSpace space) {
        return new KnowledgeSpaceView(
                space.id().value(),
                space.name(),
                space.description(),
                space.status().name(),
                space.version(),
                space.createdAt());
    }

    private static String fingerprint(String name, String description) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            if (description != null) {
                digest.update(description.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
