package com.aiplatform.foundation.evaluationoperations.application.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.dto.EvaluationDatasetCandidate;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluationDatasetCandidateServiceTest {

    private static final TenantId TENANT = new TenantId(4921825928971026433L);

    @Test
    void 应只返回当前租户已发布数据集的安全摘要() {
        var service = new EvaluationDatasetCandidateService((tenantId, page) -> {
            assertEquals(TENANT, tenantId);
            return PageResult.of(List.of(new EvaluationDatasetCandidate(
                    "opaque-dataset", 12, "SYNTHETIC", Instant.parse("2026-08-16T10:00:00Z"))), page, 1L);
        });

        var result = service.page(context(Set.of(new PermissionCode("assistant:write"))), new PageQuery(1L, 20L));

        assertEquals(12, result.items().getFirst().entryCount());
    }

    @Test
    void 普通用户不得读取评测数据集候选() {
        var service = new EvaluationDatasetCandidateService(
                (tenantId, page) -> PageResult.of(List.of(), page, 0L));

        assertThrows(IllegalArgumentException.class,
                () -> service.page(context(Set.of(new PermissionCode("knowledge:read"))), new PageQuery(1L, 20L)));
    }

    private static TrustedRequestContext context(Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                permissions,
                "trc_evaluation_dataset_candidate");
    }
}
