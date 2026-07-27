package com.aiplatform.foundation.bootstrap.controlplane.web;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import com.aiplatform.foundation.assistantdelivery.application.AssistantConcurrentModificationException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantIdempotencyConflictException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantNameConflictException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantNotFoundException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantReferenceUnavailableException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantVersionNotFoundException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantVersionQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionStateException;
import com.aiplatform.foundation.evaluationoperations.domain.authorization.OperationsPermissionDeniedException;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceNameConflictException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.content.InvalidTextContentException;
import com.aiplatform.foundation.knowledgedata.application.content.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.content.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.content.TextContentProcessingException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgeSpaceNameConflictException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgeSpaceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexEmbeddingDeploymentUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.NoPublishedSourceException;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceStateException;
import com.aiplatform.foundation.modelruntime.application.catalog.common.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.ModelDeploymentConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.ModelDeploymentNotFoundException;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.ModelDeploymentProviderUnavailableException;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.ModelDeploymentQuotaExceededException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionQuotaExceededException;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStateException;
import com.aiplatform.foundation.organizationaccess.application.authentication.InvalidCredentialsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 把内部异常归一为不泄漏资源和凭据事实的公共错误。
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private final EntityIdGenerator identifiers;

    public GlobalApiExceptionHandler(EntityIdGenerator identifiers) {
        this.identifiers = identifiers;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials() {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "认证失败");
    }

    @ExceptionHandler(InvalidAuthenticatedSessionException.class)
    ResponseEntity<ApiErrorResponse> invalidSession() {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "需要有效会话");
    }

    @ExceptionHandler(KnowledgePermissionDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限");
    }

    @ExceptionHandler(ModelCatalogPermissionDeniedException.class)
    ResponseEntity<ApiErrorResponse> modelCatalogAccessDenied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限");
    }

    @ExceptionHandler(AssistantPermissionDeniedException.class)
    ResponseEntity<ApiErrorResponse> assistantAccessDenied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限");
    }

    @ExceptionHandler(OperationsPermissionDeniedException.class)
    ResponseEntity<ApiErrorResponse> operationsAccessDenied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限");
    }

    @ExceptionHandler(AssistantIdempotencyConflictException.class)
    ResponseEntity<ApiErrorResponse> assistantIdempotencyConflict() {
        return error(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                "幂等键已经用于不同请求");
    }

    @ExceptionHandler({
        AssistantNotFoundException.class,
        AssistantVersionNotFoundException.class
    })
    ResponseEntity<ApiErrorResponse> assistantResourceNotFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "目标资源不存在");
    }

    @ExceptionHandler(AssistantNameConflictException.class)
    ResponseEntity<ApiErrorResponse> assistantNameConflict() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_NAME_CONFLICT",
                "当前租户已经存在同名助手");
    }

    @ExceptionHandler(AssistantConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> assistantVersionConflict() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_VERSION_CONFLICT",
                "助手或助手版本状态已经变化");
    }

    @ExceptionHandler(AssistantReferenceUnavailableException.class)
    ResponseEntity<ApiErrorResponse> assistantReferenceUnavailable() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_REFERENCE_UNAVAILABLE",
                "助手绑定的模型或知识索引当前不可用");
    }

    @ExceptionHandler(AssistantVersionStateException.class)
    ResponseEntity<ApiErrorResponse> assistantVersionStateConflict() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_VERSION_STATE_CONFLICT",
                "助手版本当前状态不允许该操作");
    }

    @ExceptionHandler(AssistantQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> assistantQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "ASSISTANT_QUOTA_EXCEEDED",
                "助手数量已经达到租户配额");
    }

    @ExceptionHandler(AssistantVersionQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> assistantVersionQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "ASSISTANT_VERSION_QUOTA_EXCEEDED",
                "助手版本数量已经达到配额");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ApiErrorResponse> idempotencyConflict() {
        return error(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                "幂等键已经用于不同请求");
    }

    @ExceptionHandler(ProviderConnectionIdempotencyConflictException.class)
    ResponseEntity<ApiErrorResponse> providerConnectionIdempotencyConflict() {
        return error(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                "幂等键已经用于不同请求");
    }

    @ExceptionHandler(ProviderConnectionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> providerConnectionNotFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "目标资源不存在");
    }

    @ExceptionHandler(ProviderConnectionNameConflictException.class)
    ResponseEntity<ApiErrorResponse> providerConnectionNameConflict() {
        return error(
                HttpStatus.CONFLICT,
                "PROVIDER_CONNECTION_NAME_CONFLICT",
                "当前租户已经存在同名供应商连接");
    }

    @ExceptionHandler(ProviderConnectionConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> providerConnectionVersionConflict() {
        return error(
                HttpStatus.CONFLICT,
                "PROVIDER_CONNECTION_VERSION_CONFLICT",
                "供应商连接版本已经变化");
    }

    @ExceptionHandler(ProviderConnectionQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> providerConnectionQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "PROVIDER_CONNECTION_QUOTA_EXCEEDED",
                "供应商连接数量已经达到租户配额");
    }

    @ExceptionHandler(ModelDeploymentIdempotencyConflictException.class)
    ResponseEntity<ApiErrorResponse> modelDeploymentIdempotencyConflict() {
        return error(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                "幂等键已经用于不同请求");
    }

    @ExceptionHandler(ModelDeploymentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> modelDeploymentNotFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "目标资源不存在");
    }

    @ExceptionHandler(ModelDeploymentNameConflictException.class)
    ResponseEntity<ApiErrorResponse> modelDeploymentNameConflict() {
        return error(
                HttpStatus.CONFLICT,
                "MODEL_DEPLOYMENT_NAME_CONFLICT",
                "当前租户已经存在同名模型部署");
    }

    @ExceptionHandler(ModelDeploymentConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> modelDeploymentVersionConflict() {
        return error(
                HttpStatus.CONFLICT,
                "MODEL_DEPLOYMENT_VERSION_CONFLICT",
                "模型部署版本已经变化");
    }

    @ExceptionHandler(ModelDeploymentProviderUnavailableException.class)
    ResponseEntity<ApiErrorResponse> modelDeploymentProviderUnavailable() {
        return error(
                HttpStatus.CONFLICT,
                "MODEL_DEPLOYMENT_PROVIDER_UNAVAILABLE",
                "模型部署冻结的供应商连接不可用");
    }

    @ExceptionHandler(ModelDeploymentStateException.class)
    ResponseEntity<ApiErrorResponse> modelDeploymentStateConflict() {
        return error(
                HttpStatus.CONFLICT,
                "MODEL_DEPLOYMENT_STATE_CONFLICT",
                "模型部署当前状态不允许该操作");
    }

    @ExceptionHandler(ModelDeploymentQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> modelDeploymentQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "MODEL_DEPLOYMENT_QUOTA_EXCEEDED",
                "模型部署数量已经达到租户配额");
    }

    @ExceptionHandler(KnowledgeSpaceNameConflictException.class)
    ResponseEntity<ApiErrorResponse> knowledgeSpaceNameConflict() {
        return error(
                HttpStatus.CONFLICT,
                "KNOWLEDGE_SPACE_NAME_CONFLICT",
                "当前租户已经存在同名知识空间");
    }

    @ExceptionHandler(KnowledgeSpaceQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> knowledgeSpaceQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "KNOWLEDGE_SPACE_QUOTA_EXCEEDED",
                "知识空间数量已经达到租户配额");
    }

    @ExceptionHandler(DataSourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> dataSourceNotFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "目标资源不存在");
    }

    @ExceptionHandler(DataSourceNameConflictException.class)
    ResponseEntity<ApiErrorResponse> dataSourceNameConflict() {
        return error(
                HttpStatus.CONFLICT,
                "DATA_SOURCE_NAME_CONFLICT",
                "当前知识空间已经存在同名数据源");
    }

    @ExceptionHandler(DataSourceQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> dataSourceQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "DATA_SOURCE_QUOTA_EXCEEDED",
                "数据源数量已经达到租户配额");
    }

    @ExceptionHandler(DataSourceConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> dataSourceVersionConflict() {
        return error(
                HttpStatus.CONFLICT,
                "DATA_SOURCE_VERSION_CONFLICT",
                "数据源版本已经变化");
    }

    @ExceptionHandler(DataSourceStateException.class)
    ResponseEntity<ApiErrorResponse> dataSourceStateConflict() {
        return error(
                HttpStatus.CONFLICT,
                "DATA_SOURCE_STATE_CONFLICT",
                "数据源当前状态不允许该操作");
    }

    @ExceptionHandler(IndexVersionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> indexVersionNotFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "目标资源不存在");
    }

    @ExceptionHandler(IndexVersionConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> indexVersionConflict() {
        return error(
                HttpStatus.CONFLICT,
                "INDEX_VERSION_CONFLICT",
                "索引版本状态或修订已经变化");
    }

    @ExceptionHandler(IndexEmbeddingDeploymentUnavailableException.class)
    ResponseEntity<ApiErrorResponse> indexEmbeddingDeploymentUnavailable() {
        return error(
                HttpStatus.CONFLICT,
                "EMBEDDING_DEPLOYMENT_UNAVAILABLE",
                "索引只能使用当前租户已发布的嵌入部署");
    }

    @ExceptionHandler(NoPublishedSourceException.class)
    ResponseEntity<ApiErrorResponse> noPublishedSource() {
        return error(
                HttpStatus.CONFLICT,
                "NO_PUBLISHED_SOURCE",
                "知识空间没有可构建索引的已发布内容");
    }

    @ExceptionHandler(InvalidTextContentException.class)
    ResponseEntity<ApiErrorResponse> invalidTextContent() {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_TEXT_CONTENT",
                "文本内容不符合上传要求");
    }

    @ExceptionHandler(ObjectStorageUnavailableException.class)
    ResponseEntity<ApiErrorResponse> objectStorageUnavailable() {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OBJECT_STORAGE_UNAVAILABLE",
                "对象存储当前不可用");
    }

    @ExceptionHandler(TextContentIntegrityException.class)
    ResponseEntity<ApiErrorResponse> textContentIntegrityFailure() {
        return error(
                HttpStatus.BAD_GATEWAY,
                "TEXT_CONTENT_INTEGRITY_FAILURE",
                "文本内容完整性核验失败");
    }

    @ExceptionHandler(TextContentProcessingException.class)
    ResponseEntity<ApiErrorResponse> textContentProcessingFailure() {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TEXT_CONTENT_PROCESSING_FAILURE",
                "文本内容处理暂时失败");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式不正确");
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(
                        code,
                        message,
                        TraceIds.next(identifiers),
                        Map.of()));
    }
}
