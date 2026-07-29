package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedFileDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.GovernedFileDataSourceReadPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.query.ListGovernedFileDataSourcesQuery;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GovernedFileDataSourceQueryServiceTest {

    @Test
    void 已授权成员只能按可信租户和知识空间分页读取非敏感摘要() {
        var reads = mock(GovernedFileDataSourceReadPort.class);
        var service = new GovernedFileDataSourceQueryService(reads);
        var context = mock(TrustedRequestContext.class);
        var tenant = new TenantId(100000000000000001L);
        when(context.hasPermission(new PermissionCode("knowledge:read"))).thenReturn(true);
        when(context.tenantId()).thenReturn(tenant);
        var expected = PageResult.of(List.of(new GovernedFileDataSourceSummary(
                "3001", "2001", "门店文件", "READY", 5, null,
                "4001", "PASS", Instant.parse("2026-07-29T02:05:00Z"))), new PageQuery(1, 20), 1);
        when(reads.list(any(), any(), any())).thenReturn(expected);

        var result = service.list(new ListGovernedFileDataSourcesQuery(
                context, "2001", new PageQuery(1, 20)));

        var page = ArgumentCaptor.forClass(PageQuery.class);
        verify(reads).list(org.mockito.ArgumentMatchers.eq(tenant),
                org.mockito.ArgumentMatchers.any(), page.capture());
        assertThat(page.getValue()).isEqualTo(new PageQuery(1, 20));
        assertThat(result.items()).containsExactlyElementsOf(expected.items());
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void 无知识读取权限时不触碰数据源读取端口() {
        var reads = mock(GovernedFileDataSourceReadPort.class);
        var service = new GovernedFileDataSourceQueryService(reads);
        var context = mock(TrustedRequestContext.class);
        when(context.hasPermission(new PermissionCode("knowledge:read"))).thenReturn(false);

        assertThatThrownBy(() -> service.list(new ListGovernedFileDataSourcesQuery(
                context, "2001", new PageQuery(1, 20))))
                .isInstanceOf(KnowledgePermissionDeniedException.class);

        verifyNoInteractions(reads);
    }
}
