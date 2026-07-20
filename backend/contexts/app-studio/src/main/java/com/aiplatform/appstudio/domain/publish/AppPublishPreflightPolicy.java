package com.aiplatform.appstudio.domain.publish;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AppPublishPreflightPolicy {
    public static final String KNOWLEDGE_BASE_REQUIRED = "knowledge_base_required";
    public static final String KNOWLEDGE_BASE_NOT_VISIBLE = "knowledge_base_not_visible";
    public static final String KNOWLEDGE_BASE_NOT_READY = "knowledge_base_not_ready";
    public static final String MODEL_POLICY_DENIED = "model_policy_denied";

    public AppPublishPreflightDecision evaluate(
            AiApplication application,
            KnowledgeBaseAccessCheckResponse knowledgeAccess,
            AppModelUsageDecision modelDecision
    ) {
        Objects.requireNonNull(application, "AI 应用不能为空");
        Objects.requireNonNull(knowledgeAccess, "知识库访问检查结果不能为空");
        Objects.requireNonNull(modelDecision, "模型策略决策不能为空");
        List<PublishPreflightIssue> blockers = new ArrayList<>();
        if (application.knowledgeBaseIds().isEmpty()) {
            blockers.add(new PublishPreflightIssue(
                    KNOWLEDGE_BASE_REQUIRED,
                    "应用至少需要绑定一个可用知识库",
                    "AI_APPLICATION",
                    application.applicationId()
            ));
        }
        knowledgeAccess.items().stream()
                .filter(item -> !item.accessible())
                .map(AppPublishPreflightPolicy::knowledgeIssue)
                .forEach(blockers::add);
        if (!modelDecision.allowed()) {
            blockers.add(new PublishPreflightIssue(
                    MODEL_POLICY_DENIED,
                    modelDecision.reason(),
                    "MODEL_CONFIG",
                    application.modelConfigId()
            ));
        }
        return new AppPublishPreflightDecision(blockers.isEmpty(), blockers, List.of());
    }

    private static PublishPreflightIssue knowledgeIssue(KnowledgeBaseAccessCheckItem item) {
        boolean notReady = "knowledge_base_not_published".equals(item.reasonCode());
        return new PublishPreflightIssue(
                notReady ? KNOWLEDGE_BASE_NOT_READY : KNOWLEDGE_BASE_NOT_VISIBLE,
                notReady ? "绑定知识库尚未发布" : "绑定知识库不存在或当前主体不可见",
                "KNOWLEDGE_BASE",
                item.knowledgeBaseId()
        );
    }
}
