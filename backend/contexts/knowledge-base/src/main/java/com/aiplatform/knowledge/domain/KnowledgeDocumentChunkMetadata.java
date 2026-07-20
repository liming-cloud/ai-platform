package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 知识切片检索元数据，承载 RAG 权限过滤、索引版本和引用定位所需的强制字段。
 */
public record KnowledgeDocumentChunkMetadata(
        String sourceUri,
        int documentVersion,
        String titlePath,
        Integer paragraphNo,
        String tableCellRange,
        String classification,
        String visibility,
        List<String> aclTokens,
        boolean authoritative,
        Instant publishedAt,
        Instant expiresAt,
        String embeddingModel,
        String chunkStrategyVersion,
        String indexVersion
) implements ValueObject {
    public KnowledgeDocumentChunkMetadata {
        sourceUri = requireText(sourceUri, "来源地址");
        if (documentVersion < 1) {
            throw new IllegalArgumentException("文档版本号必须为正数");
        }
        titlePath = requireText(titlePath, "标题路径");
        tableCellRange = optionalText(tableCellRange, "表格单元格范围");
        classification = requireText(classification, "知识密级");
        visibility = requireText(visibility, "知识可见性");
        Objects.requireNonNull(aclTokens, "权限快照不能为空");
        aclTokens = aclTokens.stream()
                .map(KnowledgeDocumentChunkMetadata::requireAclToken)
                .distinct()
                .toList();
        if (aclTokens.isEmpty()) {
            throw new IllegalArgumentException("权限快照不能为空");
        }
        Objects.requireNonNull(publishedAt, "发布时间不能为空");
        if (expiresAt != null && expiresAt.isBefore(publishedAt)) {
            throw new IllegalArgumentException("过期时间不能早于发布时间");
        }
        embeddingModel = requireText(embeddingModel, "Embedding 模型");
        chunkStrategyVersion = requireText(chunkStrategyVersion, "切块策略版本");
        indexVersion = requireText(indexVersion, "索引版本");
    }

    public boolean visibleAt(Instant now) {
        Objects.requireNonNull(now, "校验时间不能为空");
        return !publishedAt.isAfter(now) && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean allowsAny(List<String> principalAclTokens) {
        Objects.requireNonNull(principalAclTokens, "访问者权限标签不能为空");
        return principalAclTokens.stream().anyMatch(aclTokens::contains);
    }

    private static String requireAclToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("权限快照不能为空白");
        }
        return token.trim();
    }

    private static String optionalText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        return requireText(value, fieldName);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
