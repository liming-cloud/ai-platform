package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgeSpaceNameConflictException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgeSpaceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceCreationOutcome;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceSlice;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceStatus;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务锁实现租户内幂等和配额的知识空间仓储。
 */
public final class PostgresKnowledgeSpaceRepository implements KnowledgeSpaceRepository {

    private static final int MAX_CURSOR_LENGTH = 512;

    private final KnowledgeSpaceMapper mapper;
    private final TransactionTemplate transactions;

    public PostgresKnowledgeSpaceRepository(
            KnowledgeSpaceMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "知识空间映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public KnowledgeSpaceCreationOutcome create(
            KnowledgeSpaceCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户知识空间配额必须大于零");
        }
        var result = transactions.execute(status -> createInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "知识空间创建事务没有返回结果");
    }

    @Override
    public KnowledgeSpaceSlice findPage(TenantId tenantId, String cursor, int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var fetchLimit = limit + 1;
        List<KnowledgeSpaceRow> rows;
        if (cursor == null) {
            rows = mapper.findFirstPage(tenantId.value(), fetchLimit);
        } else {
            var position = decodeCursor(cursor);
            rows = mapper.findAfter(
                    tenantId.value(),
                    position.createdAt(),
                    position.id(),
                    fetchLimit);
        }
        var hasNext = rows.size() > limit;
        var pageRows = hasNext ? rows.subList(0, limit) : rows;
        var items = pageRows.stream().map(PostgresKnowledgeSpaceRepository::toDomain).toList();
        var nextCursor = hasNext
                ? encodeCursor(pageRows.getLast().getCreatedAt(), Long.parseLong(pageRows.getLast().getId()))
                : null;
        return new KnowledgeSpaceSlice(items, nextCursor);
    }

    private KnowledgeSpaceCreationOutcome createInTransaction(
            KnowledgeSpaceCreation creation,
            int tenantLimit) {
        var space = creation.space();
        var tenantId = space.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

        var existing = mapper.findRequest(tenantId, keyHash);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) {
                throw new IdempotencyConflictException();
            }
            var replayed = mapper.findById(
                    tenantId,
                    Long.parseLong(existing.getKnowledgeSpaceId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的知识空间不存在");
            }
            return new KnowledgeSpaceCreationOutcome(toDomain(replayed), true);
        }

        if (mapper.countActive(tenantId) >= tenantLimit) {
            throw new KnowledgeSpaceQuotaExceededException();
        }
        try {
            mapper.insertSpace(
                    tenantId,
                    space.id().value(),
                    space.name(),
                    space.description(),
                    space.status().name(),
                    space.version(),
                    space.createdBy().value(),
                    space.createdAt());
            mapper.insertRequest(
                    tenantId,
                    keyHash,
                    creation.requestFingerprint(),
                    space.id().value(),
                    space.createdAt());
        } catch (DuplicateKeyException exception) {
            throw new KnowledgeSpaceNameConflictException();
        }
        return new KnowledgeSpaceCreationOutcome(space, false);
    }

    private static KnowledgeSpace toDomain(KnowledgeSpaceRow row) {
        return KnowledgeSpace.reconstitute(
                TenantId.parse(row.getTenantId()),
                KnowledgeSpaceId.parse(row.getId()),
                row.getName(),
                row.getDescription(),
                KnowledgeSpaceStatus.valueOf(row.getStatus()),
                row.getVersion(),
                MembershipId.parse(row.getCreatedBy()),
                row.getCreatedAt());
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String encodeCursor(Instant createdAt, long id) {
        var payload = createdAt + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorPosition decodeCursor(String cursor) {
        if (cursor == null
                || cursor.isBlank()
                || cursor.length() > MAX_CURSOR_LENGTH
                || !cursor.equals(cursor.trim())) {
            throw new IllegalArgumentException("游标格式不正确");
        }
        try {
            var payload = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            var separator = payload.lastIndexOf('|');
            if (separator < 1 || separator == payload.length() - 1) {
                throw new IllegalArgumentException("游标格式不正确");
            }
            return new CursorPosition(
                    Instant.parse(payload.substring(0, separator)),
                    Long.parseLong(payload.substring(separator + 1)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("游标格式不正确", exception);
        }
    }

    private record CursorPosition(Instant createdAt, long id) {
    }
}
