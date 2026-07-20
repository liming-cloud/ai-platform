package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.application.result.CitationResult;
import com.aiplatform.appstudio.application.result.RetrievalEvidenceResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunEvidenceSnapshot;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * app_chat_run.finalization_json / result_json 的显式 v1 codec。载荷只保存 Tx B 所需事实和完成结果，
 * 禁止保存拼接 prompt 或运行时凭据。
 */
final class AppChatRunFinalizationCodec {
    static final int SCHEMA_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private static final int MAX_COLLECTION_ITEMS = 100;
    private static final int MAX_METADATA_ENTRIES = 100;
    private static final int MAX_METADATA_VALUE_LENGTH = 4_096;

    private final JsonMapper objectMapper;

    AppChatRunFinalizationCodec(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON mapper 不能为空");
    }

    String write(Finalization finalization) {
        StoredFinalizationV1 stored = toStored(Objects.requireNonNull(finalization, "聊天完成事实不能为空"));
        validate(stored);
        try {
            String json = objectMapper.writeValueAsString(stored);
            requireBoundedPayload(json);
            return json;
        } catch (JacksonException exception) {
            throw new IllegalStateException("聊天完成事实载荷无法序列化", exception);
        }
    }

    Finalization read(String json) {
        try {
            String requiredJson = requireBoundedPayload(json);
            StoredFinalizationV1 stored = objectMapper.readerFor(StoredFinalizationV1.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(requiredJson);
            validate(stored);
            return toDomain(stored);
        } catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidFinalizationPayloadException(exception);
        }
    }

    String writeCompletedResult(ChatMessageResult result) {
        StoredCompletedResultV1 stored = new StoredCompletedResultV1(
                SCHEMA_VERSION,
                StoredResultV1.fromDomain(Objects.requireNonNull(result, "聊天完成结果不能为空"))
        );
        stored.validate();
        try {
            String json = objectMapper.writeValueAsString(stored);
            requireBoundedResultPayload(json);
            return json;
        } catch (JacksonException exception) {
            throw new IllegalStateException("聊天完成结果载荷无法序列化", exception);
        }
    }

    ChatMessageResult readCompletedResult(String json) {
        try {
            String requiredJson = requireBoundedResultPayload(json);
            StoredCompletedResultV1 stored = objectMapper.readerFor(StoredCompletedResultV1.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(requiredJson);
            stored.validate();
            return stored.result().toDomain();
        } catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("聊天完成结果载荷无法读取", exception);
        }
    }

    private static StoredFinalizationV1 toStored(Finalization finalization) {
        ChatMessage user = finalization.userMessage();
        ChatMessage assistant = finalization.assistantMessage();
        AppStudioDebugRunSnapshot snapshot = finalization.debugSnapshot();
        ChatMessageResult result = finalization.result();
        return new StoredFinalizationV1(
                SCHEMA_VERSION,
                user.tenantId().value(),
                user.sessionId(),
                result.applicationId(),
                user.messageId(),
                user.content(),
                user.clientMessageId(),
                user.traceId(),
                user.createdAt(),
                assistant.messageId(),
                assistant.content(),
                assistant.citations().stream().map(StoredCitationV1::fromDomain).toList(),
                assistant.modelInvocationLogId(),
                assistant.traceId(),
                assistant.createdAt(),
                snapshot.traceId(),
                snapshot.createdAt(),
                snapshot.evidences().stream().map(StoredEvidenceV1::fromSnapshot).toList(),
                StoredResultV1.fromDomain(result)
        );
    }

    private static Finalization toDomain(StoredFinalizationV1 stored) {
        TenantId tenantId = TenantId.of(stored.tenantId());
        ChatMessage user = new ChatMessage(
                tenantId,
                stored.userMessageId(),
                stored.sessionId(),
                ChatRole.USER,
                stored.userContent(),
                List.of(),
                null,
                stored.userClientMessageId(),
                stored.userTraceId(),
                stored.userCreatedAt()
        );
        ChatMessage assistant = new ChatMessage(
                tenantId,
                stored.assistantMessageId(),
                stored.sessionId(),
                ChatRole.ASSISTANT,
                stored.assistantContent(),
                stored.assistantCitations().stream().map(StoredCitationV1::toDomain).toList(),
                stored.assistantModelInvocationLogId(),
                null,
                stored.assistantTraceId(),
                stored.assistantCreatedAt()
        );
        AppStudioDebugRunSnapshot snapshot = new AppStudioDebugRunSnapshot(
                tenantId,
                stored.assistantMessageId(),
                stored.snapshotTraceId(),
                stored.snapshotCreatedAt(),
                stored.snapshotEvidences().stream().map(StoredEvidenceV1::toSnapshot).toList()
        );
        return new Finalization(user, assistant, snapshot, stored.result().toDomain());
    }

    private static void validate(StoredFinalizationV1 stored) {
        Objects.requireNonNull(stored, "完成事实载荷不能为空");
        if (stored.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的完成事实 schema 版本");
        }
        requireDatabaseId(stored.tenantId(), "租户 ID");
        requireDatabaseId(stored.sessionId(), "会话 ID");
        requireDatabaseId(stored.applicationId(), "应用 ID");
        requireDatabaseId(stored.userMessageId(), "用户消息 ID");
        requireText(stored.userContent(), "用户消息内容");
        requireText(stored.userClientMessageId(), "客户端消息 ID");
        requireBoundedText(stored.userTraceId(), "用户消息 Trace ID", 64);
        Objects.requireNonNull(stored.userCreatedAt(), "用户消息时间不能为空");
        requireDatabaseId(stored.assistantMessageId(), "助手消息 ID");
        requireText(stored.assistantContent(), "助手消息内容");
        requireOptionalDatabaseId(stored.assistantModelInvocationLogId(), "模型调用日志 ID");
        requireBoundedText(stored.assistantTraceId(), "助手消息 Trace ID", 64);
        Objects.requireNonNull(stored.assistantCreatedAt(), "助手消息时间不能为空");
        requireBoundedText(stored.snapshotTraceId(), "快照 Trace ID", 64);
        Objects.requireNonNull(stored.snapshotCreatedAt(), "快照时间不能为空");
        boundedList(stored.assistantCitations(), "助手引用").forEach(citation ->
                Objects.requireNonNull(citation, "助手引用项不能为空").validate());
        boundedList(stored.snapshotEvidences(), "快照证据").forEach(evidence ->
                Objects.requireNonNull(evidence, "快照证据项不能为空").validate());
        StoredResultV1 result = Objects.requireNonNull(stored.result(), "完成结果不能为空");
        result.validate();
        if (!stored.userMessageId().equals(result.userMessageId())
                || !stored.assistantMessageId().equals(result.assistantMessageId())
                || !stored.sessionId().equals(result.sessionId())
                || !stored.applicationId().equals(result.applicationId())) {
            throw new IllegalArgumentException("完成结果身份与 payload 不一致");
        }
    }

    private static String requireBoundedPayload(String json) {
        String required = requireText(json, "聊天完成事实载荷");
        if (required.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("聊天完成事实载荷超过 1 MiB 上限");
        }
        return required;
    }

    private static String requireBoundedResultPayload(String json) {
        String required = requireText(json, "聊天完成结果载荷");
        if (required.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("聊天完成结果载荷超过 1 MiB 上限");
        }
        return required;
    }

    static <T> List<T> boundedList(List<T> values, String fieldName) {
        List<T> required = List.copyOf(Objects.requireNonNull(values, fieldName + "不能为空"));
        if (required.size() > MAX_COLLECTION_ITEMS) {
            throw new IllegalArgumentException(fieldName + "数量超过上限");
        }
        return required;
    }

    static Map<String, Object> safeMetadata(Map<String, Object> values) {
        Objects.requireNonNull(values, "结果 metadata 不能为空");
        if (values.size() > MAX_METADATA_ENTRIES) {
            throw new IllegalArgumentException("结果 metadata 数量超过上限");
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String requiredKey = requireText(key, "metadata key");
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("结果 metadata 仅允许标量值");
            }
            if ((value instanceof Double doubleValue && !Double.isFinite(doubleValue))
                    || (value instanceof Float floatValue && !Float.isFinite(floatValue))) {
                throw new IllegalArgumentException("结果 metadata 数值必须为有限值");
            }
            if (String.valueOf(value).length() > MAX_METADATA_VALUE_LENGTH) {
                throw new IllegalArgumentException("结果 metadata value 超过长度上限");
            }
            copied.put(requiredKey, value);
        });
        return Map.copyOf(copied);
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    static String requireDatabaseId(String value, String fieldName) {
        String normalized = requireText(value, fieldName);
        if (parseRequiredLong(normalized, fieldName) < 1) {
            throw new IllegalArgumentException(fieldName + " 必须是正整数数据库主键");
        }
        return normalized;
    }

    private static void requireOptionalDatabaseId(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            requireDatabaseId(value, fieldName);
        }
    }

    static String requireBoundedText(String value, String fieldName, int maxLength) {
        String normalized = requireText(value, fieldName);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

}

final class InvalidFinalizationPayloadException extends IllegalStateException {
    InvalidFinalizationPayloadException(Throwable cause) {
        super("聊天完成事实载荷无法读取", cause);
    }
}

record StoredFinalizationV1(
        int schemaVersion,
        String tenantId,
        String sessionId,
        String applicationId,
        String userMessageId,
        String userContent,
        String userClientMessageId,
        String userTraceId,
        Instant userCreatedAt,
        String assistantMessageId,
        String assistantContent,
        List<StoredCitationV1> assistantCitations,
        String assistantModelInvocationLogId,
        String assistantTraceId,
        Instant assistantCreatedAt,
        String snapshotTraceId,
        Instant snapshotCreatedAt,
        List<StoredEvidenceV1> snapshotEvidences,
        StoredResultV1 result
) {
}

record StoredCompletedResultV1(
        int schemaVersion,
        StoredResultV1 result
) {
    void validate() {
        if (schemaVersion != AppChatRunFinalizationCodec.SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的聊天完成结果 schema 版本");
        }
        Objects.requireNonNull(result, "聊天完成结果不能为空").validate();
    }
}

record StoredResultV1(
        String userMessageId,
        String assistantMessageId,
        String sessionId,
        String applicationId,
        String answer,
        boolean answerable,
        List<StoredCitationV1> citations,
        List<StoredEvidenceV1> evidences,
        String noAnswerReason,
        int filteredCitationCount,
        Instant createdAt,
        Map<String, Object> metadata
) {
    static StoredResultV1 fromDomain(ChatMessageResult result) {
        return new StoredResultV1(
                result.userMessageId(),
                result.assistantMessageId(),
                result.sessionId(),
                result.applicationId(),
                result.answer(),
                result.answerable(),
                result.citations().stream().map(StoredCitationV1::fromResult).toList(),
                result.evidences().stream().map(StoredEvidenceV1::fromResult).toList(),
                result.noAnswerReason(),
                result.filteredCitationCount(),
                result.createdAt(),
                AppChatRunFinalizationCodec.safeMetadata(result.metadata())
        );
    }

    void validate() {
        AppChatRunFinalizationCodec.requireDatabaseId(userMessageId, "结果用户消息 ID");
        AppChatRunFinalizationCodec.requireDatabaseId(assistantMessageId, "结果助手消息 ID");
        AppChatRunFinalizationCodec.requireDatabaseId(sessionId, "结果会话 ID");
        AppChatRunFinalizationCodec.requireDatabaseId(applicationId, "结果应用 ID");
        AppChatRunFinalizationCodec.requireText(answer, "结果回答");
        AppChatRunFinalizationCodec.boundedList(citations, "结果引用").forEach(citation ->
                Objects.requireNonNull(citation, "结果引用项不能为空").validate());
        AppChatRunFinalizationCodec.boundedList(evidences, "结果证据").forEach(evidence ->
                Objects.requireNonNull(evidence, "结果证据项不能为空").validate());
        if (filteredCitationCount < 0) {
            throw new IllegalArgumentException("过滤引用数量不能小于 0");
        }
        Objects.requireNonNull(createdAt, "结果创建时间不能为空");
        AppChatRunFinalizationCodec.safeMetadata(metadata);
    }

    ChatMessageResult toDomain() {
        return new ChatMessageResult(
                userMessageId,
                assistantMessageId,
                sessionId,
                applicationId,
                answer,
                answerable,
                citations.stream().map(StoredCitationV1::toResult).toList(),
                evidences.stream().map(StoredEvidenceV1::toResult).toList(),
                noAnswerReason,
                filteredCitationCount,
                createdAt,
                AppChatRunFinalizationCodec.safeMetadata(metadata)
        );
    }
}

record StoredCitationV1(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String documentName,
        String fragment,
        String location,
        double score
) {
    static StoredCitationV1 fromDomain(Citation citation) {
        return new StoredCitationV1(
                citation.knowledgeBaseId(), citation.documentId(), citation.chunkId(), citation.documentName(),
                citation.fragment(), citation.location(), citation.score()
        );
    }

    static StoredCitationV1 fromResult(CitationResult citation) {
        return new StoredCitationV1(
                citation.knowledgeBaseId(), citation.documentId(), citation.chunkId(), citation.title(),
                citation.snippet(), citation.locator(), citation.score()
        );
    }

    void validate() {
        AppChatRunFinalizationCodec.requireDatabaseId(knowledgeBaseId, "引用知识库 ID");
        AppChatRunFinalizationCodec.requireDatabaseId(documentId, "引用文档 ID");
        AppChatRunFinalizationCodec.requireDatabaseId(chunkId, "引用切片 ID");
        AppChatRunFinalizationCodec.requireText(documentName, "引用文档名称");
        AppChatRunFinalizationCodec.requireText(fragment, "引用片段");
        AppChatRunFinalizationCodec.requireText(location, "引用位置");
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("引用分数必须在 0 到 1 之间");
        }
    }

    Citation toDomain() {
        return new Citation(knowledgeBaseId, documentId, chunkId, documentName, fragment, location, score);
    }

    CitationResult toResult() {
        return new CitationResult(knowledgeBaseId, documentId, chunkId, documentName, fragment, location, score);
    }
}

record StoredEvidenceV1(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        double score,
        String retrievalSignal,
        String indexVersion
) {
    static StoredEvidenceV1 fromSnapshot(AppStudioDebugRunEvidenceSnapshot evidence) {
        return new StoredEvidenceV1(
                evidence.knowledgeBaseId(), evidence.documentId(), evidence.chunkId(), evidence.score(),
                evidence.retrievalSignal(), evidence.indexVersion()
        );
    }

    static StoredEvidenceV1 fromResult(RetrievalEvidenceResult evidence) {
        return new StoredEvidenceV1(
                evidence.knowledgeBaseId(), evidence.documentId(), evidence.chunkId(), evidence.score(),
                evidence.retrievalSignal(), evidence.indexVersion()
        );
    }

    void validate() {
        AppChatRunFinalizationCodec.requireDatabaseId(knowledgeBaseId, "证据知识库 ID");
        AppChatRunFinalizationCodec.requireDatabaseId(documentId, "证据文档 ID");
        AppChatRunFinalizationCodec.requireDatabaseId(chunkId, "证据切片 ID");
        if (!Double.isFinite(score) || score < 0) {
            throw new IllegalArgumentException("证据分数必须为非负有限值");
        }
        BigDecimal storedScore = BigDecimal.valueOf(score).setScale(6, RoundingMode.HALF_UP);
        if (storedScore.precision() > 10) {
            throw new IllegalArgumentException("证据分数超过 NUMERIC(10,6) 存储范围");
        }
        AppChatRunFinalizationCodec.requireBoundedText(retrievalSignal, "证据检索信号", 64);
        AppChatRunFinalizationCodec.requireBoundedText(indexVersion, "证据索引版本", 128);
    }

    AppStudioDebugRunEvidenceSnapshot toSnapshot() {
        return new AppStudioDebugRunEvidenceSnapshot(
                knowledgeBaseId, documentId, chunkId, score, retrievalSignal, indexVersion
        );
    }

    RetrievalEvidenceResult toResult() {
        return new RetrievalEvidenceResult(
                knowledgeBaseId, documentId, chunkId, score, retrievalSignal, indexVersion
        );
    }
}
