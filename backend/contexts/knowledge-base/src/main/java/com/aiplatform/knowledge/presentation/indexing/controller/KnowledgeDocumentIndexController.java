package com.aiplatform.knowledge.presentation.indexing.controller;

import com.aiplatform.knowledge.application.command.IndexKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.indexing.service.KnowledgeDocumentIndexApplicationService;
import com.aiplatform.knowledge.presentation.indexing.response.KnowledgeDocumentIndexResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureCodes;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.web.response.ApiResponse;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档索引 HTTP 适配器，统一从服务端上下文读取租户和操作者。
 */
@RestController
public class KnowledgeDocumentIndexController {
    private final KnowledgeDocumentIndexApplicationService service;
    private final TenantFeatureGuard featureGuard;

    public KnowledgeDocumentIndexController(KnowledgeDocumentIndexApplicationService service, TenantFeatureGuard featureGuard) {
        this.service = Objects.requireNonNull(service, "文档索引应用服务不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @PostMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/index")
    public ApiResponse<KnowledgeDocumentIndexResponse> index(
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.KNOWLEDGE_BASE);
        AuthenticatedPrincipal principal = PrincipalContext.requireCurrent();
        return ApiResponse.success(KnowledgeDocumentIndexResponse.from(service.indexDocument(new IndexKnowledgeDocumentCommand(
                TenantContext.current(),
                principal.actorId(),
                principal.actorType(),
                knowledgeBaseId,
                documentId
        ))));
    }
}
