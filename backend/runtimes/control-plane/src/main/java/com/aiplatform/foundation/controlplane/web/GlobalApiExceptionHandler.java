package com.aiplatform.foundation.controlplane.web;

import com.aiplatform.foundation.organizationaccess.adapter.out.session.InvalidAuthenticatedSessionException;
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
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.NoPublishedSourceException;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceStateException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentNotFoundException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentProviderUnavailableException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentQuotaExceededException;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionQuotaExceededException;
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

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(code, message, TraceIds.next(), Map.of()));
    }
}
