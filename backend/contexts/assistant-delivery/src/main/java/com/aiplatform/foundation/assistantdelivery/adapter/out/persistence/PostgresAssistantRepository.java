package com.aiplatform.foundation.assistantdelivery.adapter.out.persistence;

import com.aiplatform.foundation.assistantdelivery.application.AssistantConcurrentModificationException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantIdempotencyConflictException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantNameConflictException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantNotFoundException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantVersionQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantSlice;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionPublicationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.PublishedAssistantSlice;
import com.aiplatform.foundation.assistantdelivery.application.port.out.PublishedAssistantSummary;
import com.aiplatform.foundation.assistantdelivery.domain.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionStatus;
import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务锁实现助手配额、连续版本和幂等语义。
 */
public final class PostgresAssistantRepository implements AssistantRepository {

    private static final int MAX_CURSOR_LENGTH = 512;
    private static final String CREATE_ASSISTANT = "CREATE_ASSISTANT";
    private static final String CREATE_VERSION = "CREATE_VERSION";
    private static final String PUBLISH_VERSION = "PUBLISH_VERSION";

    private final AssistantMapper mapper;
    private final TransactionTemplate transactions;

    public PostgresAssistantRepository(
            AssistantMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "助手映射器不能为空");
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public AssistantCreationOutcome create(
            AssistantCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "助手创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户助手配额必须大于零");
        }
        var result = transactions.execute(
                status -> createAssistantInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "助手创建事务没有返回结果");
    }

    @Override
    public Optional<Assistant> findAssistant(
            TenantId tenantId,
            AssistantId assistantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(assistantId, "助手标识不能为空");
        return Optional.ofNullable(mapper.findAssistant(
                        tenantId.value(),
                        assistantId.value()))
                .map(PostgresAssistantRepository::toAssistant);
    }

    @Override
    public AssistantSlice findPage(
            TenantId tenantId,
            String cursor,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var fetchLimit = limit + 1;
        List<AssistantRow> rows;
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
        var items = pageRows.stream()
                .map(PostgresAssistantRepository::toAssistant)
                .toList();
        var nextCursor = hasNext
                ? encodeCursor(
                        pageRows.getLast().getCreatedAt(),
                        UUID.fromString(pageRows.getLast().getId()))
                : null;
        return new AssistantSlice(items, nextCursor);
    }

    @Override
    public PublishedAssistantSlice findPublishedPage(
            TenantId tenantId,
            String cursor,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var fetchLimit = limit + 1;
        List<PublishedAssistantRow> rows;
        if (cursor == null) {
            rows = mapper.findFirstPublishedPage(tenantId.value(), fetchLimit);
        } else {
            var position = decodeCursor(cursor);
            rows = mapper.findPublishedAfter(
                    tenantId.value(),
                    position.createdAt(),
                    position.id(),
                    fetchLimit);
        }
        var hasNext = rows.size() > limit;
        var pageRows = hasNext ? rows.subList(0, limit) : rows;
        var items = pageRows.stream()
                .map(PostgresAssistantRepository::toPublishedSummary)
                .toList();
        var nextCursor = hasNext
                ? encodeCursor(
                        pageRows.getLast().getPublishedAt(),
                        UUID.fromString(pageRows.getLast().getAssistantVersionId()))
                : null;
        return new PublishedAssistantSlice(items, nextCursor);
    }

    @Override
    public AssistantVersionCreationOutcome createVersion(
            AssistantVersionCreation creation,
            int perAssistantLimit) {
        Objects.requireNonNull(creation, "助手版本创建请求不能为空");
        if (perAssistantLimit < 1) {
            throw new IllegalArgumentException("助手版本配额必须大于零");
        }
        var result = transactions.execute(
                status -> createVersionInTransaction(creation, perAssistantLimit));
        return Objects.requireNonNull(result, "助手版本创建事务没有返回结果");
    }

    @Override
    public Optional<AssistantVersion> findVersion(
            TenantId tenantId,
            AssistantVersionId assistantVersionId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        return Optional.ofNullable(mapper.findVersion(
                        tenantId.value(),
                        assistantVersionId.value()))
                .map(PostgresAssistantRepository::toVersion);
    }

    @Override
    public Optional<AssistantVersionPublicationOutcome> replayPublication(
            TenantId tenantId,
            AssistantVersionId assistantVersionId,
            String idempotencyKey,
            String requestFingerprint) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        var request = mapper.findRequest(
                tenantId.value(),
                PUBLISH_VERSION,
                sha256(idempotencyKey));
        if (request == null) {
            return Optional.empty();
        }
        requireFingerprint(request, requestFingerprint);
        if (!assistantVersionId.value().toString().equals(
                request.getAssistantVersionId())) {
            throw new AssistantIdempotencyConflictException();
        }
        var row = mapper.findVersion(
                tenantId.value(),
                assistantVersionId.value());
        if (row == null) {
            throw new IllegalStateException("幂等记录引用的助手版本不存在");
        }
        var version = toVersion(row);
        if (version.status() != AssistantVersionStatus.PUBLISHED) {
            throw new IllegalStateException("发布幂等记录引用的版本未发布");
        }
        return Optional.of(new AssistantVersionPublicationOutcome(version, true));
    }

    @Override
    public AssistantVersionPublicationOutcome publishVersion(
            AssistantVersionPublication publication) {
        Objects.requireNonNull(publication, "助手版本发布请求不能为空");
        var result = transactions.execute(
                status -> publishVersionInTransaction(publication));
        return Objects.requireNonNull(result, "助手版本发布事务没有返回结果");
    }

    private AssistantCreationOutcome createAssistantInTransaction(
            AssistantCreation creation,
            int tenantLimit) {
        var assistant = creation.assistant();
        var tenantId = assistant.tenantId().value();
        mapper.lockScope(Long.toString(tenantId));
        var keyHash = sha256(creation.idempotencyKey());
        var existing = mapper.findRequest(tenantId, CREATE_ASSISTANT, keyHash);
        if (existing != null) {
            requireFingerprint(existing, creation.requestFingerprint());
            var replayed = mapper.findAssistant(
                    tenantId,
                    UUID.fromString(existing.getAssistantId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的助手不存在");
            }
            return new AssistantCreationOutcome(toAssistant(replayed), true);
        }
        if (mapper.countAssistants(tenantId) >= tenantLimit) {
            throw new AssistantQuotaExceededException();
        }
        try {
            mapper.insertAssistant(
                    tenantId,
                    assistant.id().value(),
                    assistant.displayName(),
                    assistant.latestVersionNumber(),
                    assistant.version(),
                    assistant.createdBy().value(),
                    assistant.createdAt());
            mapper.insertRequest(
                    tenantId,
                    CREATE_ASSISTANT,
                    keyHash,
                    creation.requestFingerprint(),
                    assistant.id().value(),
                    null,
                    assistant.createdAt());
        } catch (DuplicateKeyException exception) {
            throw new AssistantNameConflictException();
        }
        return new AssistantCreationOutcome(assistant, false);
    }

    private AssistantVersionCreationOutcome createVersionInTransaction(
            AssistantVersionCreation creation,
            int perAssistantLimit) {
        var expected = creation.expectedAssistant();
        var tenantId = expected.tenantId().value();
        var assistantId = expected.id().value();
        mapper.lockScope(tenantId + ":" + assistantId);
        var keyHash = sha256(creation.idempotencyKey());
        var existing = mapper.findRequest(tenantId, CREATE_VERSION, keyHash);
        if (existing != null) {
            requireFingerprint(existing, creation.requestFingerprint());
            var replayed = mapper.findVersion(
                    tenantId,
                    UUID.fromString(existing.getAssistantVersionId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的助手版本不存在");
            }
            return new AssistantVersionCreationOutcome(toVersion(replayed), true);
        }
        var locked = mapper.findAssistantForUpdate(tenantId, assistantId);
        if (locked == null) {
            throw new AssistantNotFoundException();
        }
        var current = toAssistant(locked);
        if (current.version() != expected.version()
                || current.latestVersionNumber() != expected.latestVersionNumber()) {
            throw new AssistantConcurrentModificationException();
        }
        if (mapper.countVersions(tenantId, assistantId) >= perAssistantLimit) {
            throw new AssistantVersionQuotaExceededException();
        }
        var advanced = creation.advancedAssistant();
        var version = creation.assistantVersion();
        try {
            if (mapper.advanceAssistant(
                    tenantId,
                    assistantId,
                    advanced.latestVersionNumber(),
                    advanced.version(),
                    expected.version()) != 1) {
                throw new AssistantConcurrentModificationException();
            }
            insertVersion(version);
            mapper.insertRequest(
                    tenantId,
                    CREATE_VERSION,
                    keyHash,
                    creation.requestFingerprint(),
                    assistantId,
                    version.id().value(),
                    version.createdAt());
        } catch (DuplicateKeyException exception) {
            throw new AssistantConcurrentModificationException();
        }
        return new AssistantVersionCreationOutcome(version, false);
    }

    private AssistantVersionPublicationOutcome publishVersionInTransaction(
            AssistantVersionPublication publication) {
        var version = publication.publishedVersion();
        var tenantId = version.tenantId().value();
        mapper.lockScope(tenantId + ":" + version.id().value());
        var keyHash = sha256(publication.idempotencyKey());
        var existing = mapper.findRequest(tenantId, PUBLISH_VERSION, keyHash);
        if (existing != null) {
            requireFingerprint(existing, publication.requestFingerprint());
            var replayed = mapper.findVersion(
                    tenantId,
                    UUID.fromString(existing.getAssistantVersionId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的助手版本不存在");
            }
            return new AssistantVersionPublicationOutcome(
                    toVersion(replayed),
                    true);
        }
        if (mapper.publishVersion(
                tenantId,
                version.id().value(),
                version.status().name(),
                version.version(),
                version.publishedAt(),
                publication.expectedVersion()) != 1) {
            throw new AssistantConcurrentModificationException();
        }
        mapper.insertRequest(
                tenantId,
                PUBLISH_VERSION,
                keyHash,
                publication.requestFingerprint(),
                version.assistantId().value(),
                version.id().value(),
                version.publishedAt());
        return new AssistantVersionPublicationOutcome(version, false);
    }

    private void insertVersion(AssistantVersion version) {
        mapper.insertVersion(
                version.tenantId().value(),
                version.id().value(),
                version.assistantId().value(),
                version.number(),
                version.chatDeploymentId(),
                version.chatDeploymentRevision(),
                version.chatDeploymentOutputLimit(),
                version.knowledgeSpaceId(),
                version.indexVersionId(),
                version.indexVersionRevision(),
                version.embeddingDeploymentId(),
                version.embeddingDeploymentRevision(),
                version.embeddingDimension(),
                version.systemInstruction(),
                version.maxOutputTokens(),
                version.retrievalLimit(),
                version.minimumScore(),
                version.citationPolicy().name(),
                version.refusalPolicy().name(),
                version.status().name(),
                version.version(),
                version.createdBy().value(),
                version.createdAt(),
                version.publishedAt());
    }

    private static Assistant toAssistant(AssistantRow row) {
        return Assistant.reconstitute(
                TenantId.parse(row.getTenantId()),
                new AssistantId(UUID.fromString(row.getId())),
                row.getDisplayName(),
                row.getLatestVersionNumber(),
                row.getVersion(),
                MembershipId.parse(row.getCreatedBy()),
                row.getCreatedAt());
    }

    private static AssistantVersion toVersion(AssistantVersionRow row) {
        return AssistantVersion.reconstitute(
                TenantId.parse(row.getTenantId()),
                new AssistantVersionId(UUID.fromString(row.getId())),
                new AssistantId(UUID.fromString(row.getAssistantId())),
                row.getVersionNumber(),
                row.getChatDeploymentId(),
                row.getChatDeploymentRevision(),
                row.getChatDeploymentOutputLimit(),
                Long.parseLong(row.getKnowledgeSpaceId()),
                Long.parseLong(row.getIndexVersionId()),
                row.getIndexVersionRevision(),
                row.getEmbeddingDeploymentId(),
                row.getEmbeddingDeploymentRevision(),
                row.getEmbeddingDimension(),
                row.getSystemInstruction(),
                row.getMaxOutputTokens(),
                row.getRetrievalLimit(),
                row.getMinimumScore(),
                CitationPolicy.valueOf(row.getCitationPolicy()),
                RefusalPolicy.valueOf(row.getRefusalPolicy()),
                AssistantVersionStatus.valueOf(row.getStatus()),
                row.getVersion(),
                MembershipId.parse(row.getCreatedBy()),
                row.getCreatedAt(),
                row.getPublishedAt());
    }

    private static PublishedAssistantSummary toPublishedSummary(
            PublishedAssistantRow row) {
        return new PublishedAssistantSummary(
                UUID.fromString(row.getAssistantId()),
                row.getDisplayName(),
                UUID.fromString(row.getAssistantVersionId()),
                row.getVersionNumber(),
                row.getPublishedAt());
    }

    private static void requireFingerprint(
            AssistantRequestRow row,
            String expected) {
        if (!row.getRequestFingerprint().equals(expected)) {
            throw new AssistantIdempotencyConflictException();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String encodeCursor(Instant createdAt, UUID id) {
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
                    UUID.fromString(payload.substring(separator + 1)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("游标格式不正确", exception);
        }
    }

    private record CursorPosition(Instant createdAt, UUID id) {
    }
}
