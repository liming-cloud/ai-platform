package com.aiplatform.knowledge.presentation.controller;

import com.aiplatform.knowledge.application.KnowledgeBaseApplicationService;
import com.aiplatform.knowledge.application.command.CreateKnowledgeBaseCommand;
import com.aiplatform.knowledge.application.command.DisableKnowledgeBaseCommand;
import com.aiplatform.knowledge.application.command.UpdateKnowledgeBaseCommand;
import com.aiplatform.knowledge.presentation.dto.CreateKnowledgeBaseRequest;
import com.aiplatform.knowledge.presentation.dto.KnowledgeBaseDetailResponse;
import com.aiplatform.knowledge.presentation.dto.KnowledgeBaseListItemResponse;
import com.aiplatform.knowledge.presentation.dto.KnowledgeBaseResponse;
import com.aiplatform.knowledge.presentation.dto.UpdateKnowledgeBaseRequest;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureCodes;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库 HTTP 适配器，负责上下文租户、认证主体、请求校验和统一响应包装。
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseApplicationService service;
    private final TenantFeatureGuard featureGuard;

    public KnowledgeBaseController(KnowledgeBaseApplicationService service, TenantFeatureGuard featureGuard) {
        this.service = Objects.requireNonNull(service, "知识库应用服务不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> createKnowledgeBase(
            @Valid @RequestBody CreateKnowledgeBaseRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        AuthenticatedPrincipal principal = PrincipalContext.requireCurrent();
        return ApiResponse.success(KnowledgeBaseResponse.from(service.createKnowledgeBase(new CreateKnowledgeBaseCommand(
                TenantContext.current(),
                principal.actorId(),
                principal.actorType(),
                request.name(),
                request.visibility(),
                request.description(),
                request.embeddingModelConfigId()
        ))));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseListItemResponse>> listKnowledgeBases() {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(service.listKnowledgeBases(TenantContext.current()).stream()
                .map(KnowledgeBaseListItemResponse::from)
                .toList());
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseDetailResponse> getKnowledgeBase(@PathVariable String knowledgeBaseId) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(KnowledgeBaseDetailResponse.from(service.getKnowledgeBase(
                TenantContext.current(),
                knowledgeBaseId
        )));
    }

    @PutMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseDetailResponse> updateKnowledgeBase(
            @PathVariable String knowledgeBaseId,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        AuthenticatedPrincipal principal = PrincipalContext.requireCurrent();
        return ApiResponse.success(KnowledgeBaseDetailResponse.from(service.updateKnowledgeBase(new UpdateKnowledgeBaseCommand(
                TenantContext.current(),
                principal.actorId(),
                principal.actorType(),
                knowledgeBaseId,
                request.name(),
                request.visibility(),
                request.description(),
                request.embeddingModelConfigId()
        ))));
    }

    @PostMapping("/{knowledgeBaseId}/disable")
    public ApiResponse<KnowledgeBaseDetailResponse> disableKnowledgeBase(@PathVariable String knowledgeBaseId) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        return ApiResponse.success(KnowledgeBaseDetailResponse.from(service.disableKnowledgeBase(new DisableKnowledgeBaseCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                knowledgeBaseId
        ))));
    }
}
