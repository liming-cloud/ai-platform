package com.aiplatform.shared.security.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PermissionCodeTest {
    @Test
    void acceptsStandardRbacPermissionCode() {
        PermissionCode code = new PermissionCode("knowledge-base:document:upload");

        assertThat(code.value()).isEqualTo("knowledge-base:document:upload");
    }

    @Test
    void trimsPermissionCodeBeforeCatalogLookup() {
        PermissionCode code = new PermissionCode(" knowledge-base:document:upload ");

        assertThat(code.value()).isEqualTo("knowledge-base:document:upload");
    }

    @Test
    void rejectsBlankPermissionCodeInsteadOfGenericFormatError() {
        assertThatThrownBy(() -> new PermissionCode(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限编码不能为空");
    }

    @Test
    void rejectsInvalidPermissionCode() {
        assertThatThrownBy(() -> new PermissionCode("knowledgeBase.document.upload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限编码必须符合 context:resource:action 格式");
    }
}
