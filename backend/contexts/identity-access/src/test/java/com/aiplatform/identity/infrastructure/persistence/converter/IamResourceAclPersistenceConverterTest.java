package com.aiplatform.identity.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.identity.domain.permission.ResourceAcl;
import com.aiplatform.identity.infrastructure.persistence.entity.IamResourceAclPersistenceEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IamResourceAclPersistenceConverterTest {
    @Test
    void convertsPersistenceProjectionToDomainAcl() {
        IamResourceAclPersistenceEntity row = activeAclRow();
        row.setExpiresAt(Instant.parse("2026-06-01T00:00:00Z"));

        ResourceAcl acl = new IamResourceAclPersistenceConverter().toDomain(row);

        assertThat(acl.tenantId().value()).isEqualTo("2001");
        assertThat(acl.aclId()).isEqualTo("91");
        assertThat(acl.resourceScope().resourceType()).isEqualTo("knowledge_base");
        assertThat(acl.resourceScope().resourceId()).isEqualTo("7");
        assertThat(acl.subjectType()).isEqualTo("role");
        assertThat(acl.subjectId()).isEqualTo("3");
        assertThat(acl.permissionCode().value()).isEqualTo("knowledge-base:document:upload");
        assertThat(acl.allows()).isTrue();
    }

    @Test
    void rejectsMissingNumericFieldsInsteadOfStringValueNull() {
        IamResourceAclPersistenceEntity row = activeAclRow();
        row.setSubjectId(null);

        assertThatThrownBy(() -> new IamResourceAclPersistenceConverter().toDomain(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("授权主体 ID不能为空");
    }

    @Test
    void rejectsMissingTextFieldsInsteadOfDomainDefault() {
        IamResourceAclPersistenceEntity row = activeAclRow();
        row.setPermissionCode(" ");

        assertThatThrownBy(() -> new IamResourceAclPersistenceConverter().toDomain(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("权限编码不能为空");
    }

    private static IamResourceAclPersistenceEntity activeAclRow() {
        IamResourceAclPersistenceEntity row = new IamResourceAclPersistenceEntity();
        row.setId(91L);
        row.setTenantId(2001L);
        row.setResourceType("knowledge_base");
        row.setResourceId(7L);
        row.setSubjectType("role");
        row.setSubjectId(3L);
        row.setPermissionCode("knowledge-base:document:upload");
        row.setEffect("allow");
        return row;
    }
}
