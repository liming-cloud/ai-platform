package com.aiplatform.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SwitchKnowledgeBaseEmbeddingModelCommandTest {
    @Test
    void normalizesSwitchEmbeddingModelFactsBeforeApplicationServiceUse() {
        SwitchKnowledgeBaseEmbeddingModelCommand command = new SwitchKnowledgeBaseEmbeddingModelCommand(
                TenantId.of("2001"),
                " 20 ",
                " 1001 ",
                " user ",
                Set.of(" platform-admin "),
                Set.of(new PermissionCode("knowledge:base:write")),
                " 6 "
        );

        assertThat(command.knowledgeBaseId()).isEqualTo("20");
        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.actorType()).isEqualTo("user");
        assertThat(command.roleCodes()).containsExactly("platform-admin");
        assertThat(command.embeddingModelConfigId()).isEqualTo("6");
        assertThat(command.useCase()).isEqualTo(KnowledgeModelUseCase.RAG_INDEXING);
        assertThat(command.requiredCapability()).isEqualTo(KnowledgeModelCapability.EMBEDDING);
    }

    @Test
    void rejectsBlankTargetEmbeddingModelAndRoleSnapshot() {
        assertThatThrownBy(() -> new SwitchKnowledgeBaseEmbeddingModelCommand(
                        TenantId.of("2001"),
                        "20",
                        "1001",
                        "user",
                        Set.of("platform-admin"),
                        Set.of(new PermissionCode("knowledge:base:write")),
                        " "
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目标向量模型配置 ID不能为空");

        assertThatThrownBy(() -> new SwitchKnowledgeBaseEmbeddingModelCommand(
                        TenantId.of("2001"),
                        "20",
                        "1001",
                        "user",
                        Set.of(),
                        Set.of(new PermissionCode("knowledge:base:write")),
                        "6"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色快照不能为空");
    }
}
