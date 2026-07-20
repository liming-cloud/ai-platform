package com.aiplatform.appstudio.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppPublishPreflightPolicyTest {
    @Test
    void aggregatesKnowledgeVisibilityAndModelPolicyBlockers() {
        var decision = new AppPublishPreflightPolicy().evaluate(
                application(),
                new KnowledgeBaseAccessCheckResponse(List.of(
                        KnowledgeBaseAccessCheckItem.allowed("20"),
                        KnowledgeBaseAccessCheckItem.denied("21", "resource_not_visible")
                )),
                new AppModelUsageDecision(false, "disabled", "模型策略已禁用", null, Map.of())
        );

        assertThat(decision.passed()).isFalse();
        assertThat(decision.blockers()).extracting(PublishPreflightIssue::code)
                .containsExactly("knowledge_base_not_visible", "model_policy_denied");
    }

    private static AiApplication application() {
        Instant now = Instant.parse("2026-07-10T03:00:00Z");
        return new AiApplication(
                TenantId.of("2001"), "3001", AiApplicationType.KNOWLEDGE_QA,
                "知识助手", "知识助手描述", "6", List.of("20", "21"), List.of(), Set.of("1001"),
                "请基于知识回答", ApplicationPublicationStatus.DRAFT, now, now
        );
    }
}
