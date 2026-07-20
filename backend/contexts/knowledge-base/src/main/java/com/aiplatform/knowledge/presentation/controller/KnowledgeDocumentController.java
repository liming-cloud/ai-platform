package com.aiplatform.knowledge.presentation.controller;

import com.aiplatform.knowledge.application.KnowledgeDocumentApplicationService;
import com.aiplatform.knowledge.application.KnowledgeDocumentIngestApplicationService;
import com.aiplatform.knowledge.application.command.ParseKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.command.UploadKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.command.CancelKnowledgeDocumentCommand;
import com.aiplatform.knowledge.presentation.dto.CancelKnowledgeDocumentRequest;
import com.aiplatform.knowledge.presentation.dto.KnowledgeDocumentListItemResponse;
import com.aiplatform.knowledge.presentation.dto.KnowledgeDocumentParseResponse;
import com.aiplatform.knowledge.presentation.dto.KnowledgeDocumentResponse;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureCodes;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.web.response.ApiResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

/**
 * 知识文档 HTTP 适配器，负责从 multipart 请求读取上传文件并使用服务端上下文。
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/documents")
public class KnowledgeDocumentController {
    private static final String INVALID_UPLOAD_METADATA_CODE = "AIP-KB-UPLOADINVALID";

    private final KnowledgeDocumentApplicationService service;
    private final KnowledgeDocumentIngestApplicationService ingestService;
    private final TenantFeatureGuard featureGuard;

    public KnowledgeDocumentController(
            KnowledgeDocumentApplicationService service,
            KnowledgeDocumentIngestApplicationService ingestService,
            TenantFeatureGuard featureGuard
    ) {
        this.service = Objects.requireNonNull(service, "知识文档应用服务不能为空");
        this.ingestService = Objects.requireNonNull(ingestService, "知识文档导入应用服务不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @GetMapping
    public ApiResponse<List<KnowledgeDocumentListItemResponse>> listDocuments(@PathVariable String knowledgeBaseId) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(service.listDocuments(TenantContext.current(), knowledgeBaseId).stream()
                .map(KnowledgeDocumentListItemResponse::from)
                .toList());
    }

    @PostMapping
    public ApiResponse<KnowledgeDocumentResponse> uploadDocument(
            @PathVariable String knowledgeBaseId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(KnowledgeDocumentResponse.from(service.uploadDocument(new UploadKnowledgeDocumentCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                knowledgeBaseId,
                resolveFilename(file),
                resolveContentType(file),
                file.getBytes()
        ))));
    }

    @PostMapping("/{documentId}/parse")
    public ApiResponse<KnowledgeDocumentParseResponse> parseDocument(
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(KnowledgeDocumentParseResponse.from(ingestService.parseDocument(new ParseKnowledgeDocumentCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                knowledgeBaseId,
                documentId
        ))));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<KnowledgeDocumentListItemResponse> removeDocument(
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(KnowledgeDocumentListItemResponse.from(service.removeDocument(
                TenantContext.current(),
                knowledgeBaseId,
                documentId,
                PrincipalContext.currentActorId()
        )));
    }

    @PostMapping("/{documentId}/cancel")
    public ApiResponse<KnowledgeDocumentListItemResponse> cancelDocument(
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId,
            @Valid @RequestBody CancelKnowledgeDocumentRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(KnowledgeDocumentListItemResponse.from(service.cancelDocument(
                new CancelKnowledgeDocumentCommand(
                        TenantContext.current(),
                        PrincipalContext.currentActorId(),
                        knowledgeBaseId,
                        documentId,
                        request.reason(),
                        TraceContext.currentTraceId()
                )
        )));
    }

    private static String resolveFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw invalidUploadMetadata("上传文件名不能为空");
        }
        return originalFilename.trim();
    }

    private static String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw invalidUploadMetadata("上传文件 MIME 类型不能为空");
        }
        return contentType.trim();
    }

    private static ApplicationException invalidUploadMetadata(String message) {
        return new ApplicationException(ErrorCode.of(INVALID_UPLOAD_METADATA_CODE, message), message);
    }
}
