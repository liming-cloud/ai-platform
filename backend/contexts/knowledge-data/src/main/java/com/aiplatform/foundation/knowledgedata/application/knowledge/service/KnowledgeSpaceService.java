package com.aiplatform.foundation.knowledgedata.application.knowledge.service;

import com.aiplatform.foundation.knowledgedata.application.knowledge.command.CreateKnowledgeSpaceCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.dto.CreateKnowledgeSpaceResult;
import com.aiplatform.foundation.knowledgedata.application.knowledge.dto.KnowledgeSpaceView;
import com.aiplatform.foundation.knowledgedata.application.knowledge.query.ListKnowledgeSpacesQuery;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceCreation;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpace;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户和权限边界内编排知识空间。
 */
public final class KnowledgeSpaceService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final KnowledgeSpaceRepository repository;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;

    public KnowledgeSpaceService(
            KnowledgeSpaceRepository repository,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit) {
        this.repository = Objects.requireNonNull(repository, "知识空间仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户知识空间配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
    }

    public CreateKnowledgeSpaceResult create(CreateKnowledgeSpaceCommand command) {
        Objects.requireNonNull(command, "创建命令不能为空");
        requirePermission(command.context(), WRITE);
        var context = command.context();
        var space = KnowledgeSpace.create(
                context.tenantId(),
                new KnowledgeSpaceId(identifiers.nextId()),
                command.name(),
                command.description(),
                new CreatorId(context.membershipId().value()),
                clock.instant());
        var outcome = repository.create(
                new KnowledgeSpaceCreation(
                        space,
                        command.idempotencyKey(),
                        fingerprint(space.name(), space.description())),
                tenantLimit);
        return new CreateKnowledgeSpaceResult(toView(outcome.space()), outcome.replayed());
    }

    public PageResult<KnowledgeSpaceView> list(ListKnowledgeSpacesQuery query) {
        Objects.requireNonNull(query, "列表查询不能为空");
        requirePermission(query.context(), WRITE);
        var page = repository.findPage(
                query.context().tenantId(),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                page.items().stream().map(KnowledgeSpaceService::toView).toList(),
                query.page(),
                page.totalElements());
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
                space.id().toString(),
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
