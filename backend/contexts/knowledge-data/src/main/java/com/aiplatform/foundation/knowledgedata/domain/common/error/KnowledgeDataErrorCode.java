package com.aiplatform.foundation.knowledgedata.domain.common.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 知识数据上下文对外稳定的错误编码注册表。
 */
public enum KnowledgeDataErrorCode implements ErrorCode {

    INVALID_TEXT_CONTENT(
            "KNOWLEDGE_DATA.REQUEST.0001",
            ErrorType.REQUEST,
            "文本内容不符合上传要求",
            false),
    PERMISSION_DENIED(
            "KNOWLEDGE_DATA.AUTHORIZATION.0001",
            ErrorType.AUTHORIZATION,
            "没有执行该知识操作的权限",
            false),
    DATA_SOURCE_NOT_FOUND(
            "KNOWLEDGE_DATA.RESOURCE.0001",
            ErrorType.RESOURCE,
            "数据源不存在",
            false),
    INDEX_VERSION_NOT_FOUND(
            "KNOWLEDGE_DATA.RESOURCE.0002",
            ErrorType.RESOURCE,
            "索引版本不存在",
            false),
    NO_PUBLISHED_SOURCE(
            "KNOWLEDGE_DATA.RESOURCE.0003",
            ErrorType.RESOURCE,
            "知识空间没有已发布内容",
            false),
    EMBEDDING_DEPLOYMENT_UNAVAILABLE(
            "KNOWLEDGE_DATA.RESOURCE.0004",
            ErrorType.RESOURCE,
            "索引只能使用当前租户已发布的嵌入部署",
            false),
    IDEMPOTENCY_CONFLICT(
            "KNOWLEDGE_DATA.CONFLICT.0001",
            ErrorType.CONFLICT,
            "幂等键已经用于不同请求",
            false),
    KNOWLEDGE_SPACE_NAME_CONFLICT(
            "KNOWLEDGE_DATA.CONFLICT.0002",
            ErrorType.CONFLICT,
            "当前租户已经存在同名知识空间",
            false),
    DATA_SOURCE_NAME_CONFLICT(
            "KNOWLEDGE_DATA.CONFLICT.0003",
            ErrorType.CONFLICT,
            "当前知识空间已经存在同名数据源",
            false),
    DATA_SOURCE_VERSION_CONFLICT(
            "KNOWLEDGE_DATA.CONFLICT.0004",
            ErrorType.CONFLICT,
            "数据源版本已经变化",
            false),
    INDEX_VERSION_CONFLICT(
            "KNOWLEDGE_DATA.CONFLICT.0005",
            ErrorType.CONFLICT,
            "索引版本状态或修订已经变化",
            false),
    DATA_SOURCE_STATE_CONFLICT(
            "KNOWLEDGE_DATA.CONFLICT.0006",
            ErrorType.CONFLICT,
            "数据源当前状态不允许该操作",
            false),
    INDEX_VERSION_STATE_CONFLICT(
            "KNOWLEDGE_DATA.CONFLICT.0007",
            ErrorType.CONFLICT,
            "索引版本当前状态不允许该操作",
            false),
    KNOWLEDGE_SPACE_QUOTA_EXCEEDED(
            "KNOWLEDGE_DATA.QUOTA.0001",
            ErrorType.QUOTA,
            "知识空间数量已经达到租户配额",
            false),
    DATA_SOURCE_QUOTA_EXCEEDED(
            "KNOWLEDGE_DATA.QUOTA.0002",
            ErrorType.QUOTA,
            "数据源数量已经达到租户配额",
            false),
    OBJECT_STORAGE_UNAVAILABLE(
            "KNOWLEDGE_DATA.DEPENDENCY_UNAVAILABLE.0001",
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "对象存储当前不可用",
            true),
    TEXT_CONTENT_INTEGRITY_FAILURE(
            "KNOWLEDGE_DATA.DEPENDENCY_RESPONSE.0001",
            ErrorType.DEPENDENCY_RESPONSE,
            "文本内容完整性核验失败",
            true),
    TEXT_CONTENT_PROCESSING_FAILURE(
            "KNOWLEDGE_DATA.SYSTEM.0001",
            ErrorType.SYSTEM,
            "文本内容处理暂时失败",
            true);

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;
    private final boolean retryable;

    KnowledgeDataErrorCode(
            String code,
            ErrorType type,
            String defaultMessage,
            boolean retryable) {
        this.code = code;
        this.type = type;
        this.defaultMessage = defaultMessage;
        this.retryable = retryable;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public boolean retryable() {
        return retryable;
    }

    @Override
    public Set<String> allowedDetailFields() {
        return Set.of();
    }
}
