package com.aiplatform.knowledge.presentation.retrieval.controller;

import com.aiplatform.knowledge.application.command.RetrieveKnowledgeCommand;
import com.aiplatform.knowledge.application.retrieval.service.KnowledgeRetrievalApplicationService;
import com.aiplatform.knowledge.domain.retrieval.RetrievalProfile;
import com.aiplatform.knowledge.presentation.retrieval.request.RetrieveKnowledgeRequest;
import com.aiplatform.knowledge.presentation.retrieval.response.RetrieveKnowledgeResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureCodes;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识检索 HTTP 适配器，负责把服务端租户、主体和 ACL 快照注入检索命令。
 */
@RestController
@RequestMapping({"/api/v1/knowledge-retrieval", "/api/v1/knowledge/retrieval"})
public class KnowledgeRetrievalController {
    private final KnowledgeRetrievalApplicationService service;
    private final TenantFeatureGuard featureGuard;

    public KnowledgeRetrievalController(
            KnowledgeRetrievalApplicationService service,
            TenantFeatureGuard featureGuard
    ) {
        this.service = Objects.requireNonNull(service, "知识检索服务不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @PostMapping({"", "/search"})
    public ApiResponse<RetrieveKnowledgeResponse> retrieve(@Valid @RequestBody RetrieveKnowledgeRequest request) {
        TenantId tenantId = TenantContext.current();
        featureGuard.requireEnabled(tenantId, TenantFeatureCodes.KNOWLEDGE_BASE);
        AuthenticatedPrincipal principal = PrincipalContext.requireCurrent();
        RetrievalProfile profile = resolveProfile(request.retrievalProfile());
        return ApiResponse.success(RetrieveKnowledgeResponse.from(service.retrieve(new RetrieveKnowledgeCommand(
                tenantId,
                principal.actorId(),
                principal.actorType(),
                request.knowledgeBaseIds(),
                request.query(),
                profile,
                request.answerModel()
        ))));
    }

    private static RetrievalProfile resolveProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return RetrievalProfile.BALANCED;
        }
        return switch (profile.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> RetrievalProfile.STRICT;
            case "balanced" -> RetrievalProfile.BALANCED;
            case "exploratory" -> RetrievalProfile.EXPLORATORY;
            default -> throw new IllegalArgumentException("不支持的检索画像：" + profile);
        };
    }
}
