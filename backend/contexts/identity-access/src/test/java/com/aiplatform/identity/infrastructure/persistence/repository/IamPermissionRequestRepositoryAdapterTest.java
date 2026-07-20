package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestPage;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestQuery;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestSensitiveTextPolicy;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestStatus;
import com.aiplatform.identity.infrastructure.persistence.entity.IamPermissionRequestPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamPermissionRequestMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IamPermissionRequestRepositoryAdapterTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant CREATED_AT = Instant.parse("2026-07-10T00:00:00Z");
    private static final Instant PERSISTED_AT = Instant.parse("2026-07-10T00:05:00Z");

    @Test
    void saveWritesQuarantinedHistoricalTextAndReturnsCanonicalPersistedRow() {
        IamPermissionRequestMapper mapper = mock(IamPermissionRequestMapper.class);
        when(mapper.updateDecision(any())).thenReturn(1);
        when(mapper.findById(2001L, "request-1")).thenReturn(entity(
                2001L,
                "request-1",
                "数据库 canonical 原因",
                "数据库 canonical 意见",
                "REJECTED",
                PERSISTED_AT
        ));
        IamPermissionRequestRepositoryAdapter repository = new IamPermissionRequestRepositoryAdapter(mapper);
        PermissionRequest input = request(
                TENANT_ID,
                "request-1",
                "历史原因 password=must-not-write",
                "处理意见 token=must-not-write",
                PermissionRequestStatus.REJECTED,
                CREATED_AT
        );

        PermissionRequest saved = repository.save(input);

        ArgumentCaptor<IamPermissionRequestPersistenceEntity> entityCaptor =
                ArgumentCaptor.forClass(IamPermissionRequestPersistenceEntity.class);
        verify(mapper).updateDecision(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(entityCaptor.getValue().getRequestId()).isEqualTo("request-1");
        assertThat(entityCaptor.getValue().getReason()).isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        assertThat(entityCaptor.getValue().getDecisionComment())
                .isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        verify(mapper).findById(2001L, "request-1");
        assertThat(saved.reason()).isEqualTo("数据库 canonical 原因");
        assertThat(saved.decisionComment()).isEqualTo("数据库 canonical 意见");
        assertThat(saved.updatedAt()).isEqualTo(PERSISTED_AT);
    }

    @Test
    void queryQuarantinesLegacyReasonAndCommentBeforeReturningDomainObject() {
        IamPermissionRequestMapper mapper = mock(IamPermissionRequestMapper.class);
        when(mapper.findById(2001L, "request-legacy")).thenReturn(entity(
                2001L,
                "request-legacy",
                "历史 reason api_key=legacy-secret",
                "历史 comment eyJwYXNzd29yZCI6Imh1bnRlcjIifQ==",
                "REJECTED",
                PERSISTED_AT
        ));
        IamPermissionRequestRepositoryAdapter repository = new IamPermissionRequestRepositoryAdapter(mapper);

        PermissionRequest result = repository.findById(TENANT_ID, "request-legacy").orElseThrow();

        assertThat(result.reason()).isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        assertThat(result.decisionComment()).isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        assertThat(result.toString())
                .doesNotContain("legacy-secret")
                .doesNotContain("eyJwYXNzd29yZCI6Imh1bnRlcjIifQ==");
    }

    @Test
    void findByIdRejectsMapperRowWithWrongTenantOrRequestId() {
        IamPermissionRequestMapper wrongTenantMapper = mock(IamPermissionRequestMapper.class);
        when(wrongTenantMapper.findById(2001L, "request-1"))
                .thenReturn(entity(9001L, "request-1", "合法原因", null, "PENDING", CREATED_AT));
        IamPermissionRequestRepositoryAdapter wrongTenantRepository =
                new IamPermissionRequestRepositoryAdapter(wrongTenantMapper);

        assertThatThrownBy(() -> wrongTenantRepository.findById(TENANT_ID, "request-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("权限申请持久化结果身份不一致");

        IamPermissionRequestMapper wrongRequestMapper = mock(IamPermissionRequestMapper.class);
        when(wrongRequestMapper.findById(2001L, "request-1"))
                .thenReturn(entity(2001L, "request-other", "合法原因", null, "PENDING", CREATED_AT));
        IamPermissionRequestRepositoryAdapter wrongRequestRepository =
                new IamPermissionRequestRepositoryAdapter(wrongRequestMapper);

        assertThatThrownBy(() -> wrongRequestRepository.findById(TENANT_ID, "request-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("权限申请持久化结果身份不一致");
    }

    @Test
    void saveRejectsCanonicalRowWithWrongTenantOrRequestId() {
        IamPermissionRequestMapper mapper = mock(IamPermissionRequestMapper.class);
        when(mapper.insertRequest(any())).thenReturn(1);
        when(mapper.findById(2001L, "request-1"))
                .thenReturn(entity(2001L, "request-other", "合法原因", null, "PENDING", CREATED_AT));
        IamPermissionRequestRepositoryAdapter repository = new IamPermissionRequestRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.save(request(
                TENANT_ID,
                "request-1",
                "合法原因",
                null,
                PermissionRequestStatus.PENDING,
                CREATED_AT
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("权限申请持久化结果身份不一致");
    }

    @Test
    void listRejectsAnyForeignTenantRowInsteadOfFilteringItSilently() {
        IamPermissionRequestMapper mapper = mock(IamPermissionRequestMapper.class);
        when(mapper.countByTenant(2001L, null)).thenReturn(2L);
        when(mapper.findPage(2001L, null, 0L, 20)).thenReturn(List.of(
                entity(2001L, "request-1", "合法原因", null, "PENDING", CREATED_AT),
                entity(9001L, "request-2", "合法原因", null, "PENDING", CREATED_AT)
        ));
        IamPermissionRequestRepositoryAdapter repository = new IamPermissionRequestRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.findPage(new PermissionRequestQuery(TENANT_ID, null, 1, 20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("权限申请持久化结果身份不一致");
    }

    @Test
    void pageUsesDatabaseCountStatusFilterAndRequestedWindow() {
        IamPermissionRequestMapper mapper = mock(IamPermissionRequestMapper.class);
        when(mapper.countByTenant(2001L, "PENDING")).thenReturn(41L);
        when(mapper.findPage(2001L, "PENDING", 20L, 20)).thenReturn(List.of(
                entity(2001L, "request-21", "合法原因", null, "PENDING", CREATED_AT)
        ));
        IamPermissionRequestRepositoryAdapter repository = new IamPermissionRequestRepositoryAdapter(mapper);

        PermissionRequestPage result = repository.findPage(new PermissionRequestQuery(
                TENANT_ID,
                PermissionRequestStatus.PENDING,
                2,
                20
        ));

        assertThat(result.items()).singleElement()
                .extracting(PermissionRequest::requestId)
                .isEqualTo("request-21");
        assertThat(result.total()).isEqualTo(41);
        assertThat(result.totalPages()).isEqualTo(3);
        verify(mapper).countByTenant(2001L, "PENDING");
        verify(mapper).findPage(2001L, "PENDING", 20L, 20);
    }

    private static PermissionRequest request(
            TenantId tenantId,
            String requestId,
            String reason,
            String comment,
            PermissionRequestStatus status,
            Instant updatedAt
    ) {
        return new PermissionRequest(
                tenantId,
                requestId,
                "1001",
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use",
                reason,
                status,
                status == PermissionRequestStatus.PENDING ? null : "1002",
                comment,
                CREATED_AT,
                updatedAt
        );
    }

    private static IamPermissionRequestPersistenceEntity entity(
            Long tenantId,
            String requestId,
            String reason,
            String comment,
            String status,
            Instant updatedAt
    ) {
        IamPermissionRequestPersistenceEntity entity = new IamPermissionRequestPersistenceEntity();
        entity.setTenantId(tenantId);
        entity.setRequestId(requestId);
        entity.setRequesterId(1001L);
        entity.setResourceType("AI_APPLICATION");
        entity.setResourceId("3001");
        entity.setPermissionCode("app-studio:application:use");
        entity.setReason(reason);
        entity.setStatus(status);
        entity.setReviewerId("PENDING".equals(status) ? null : 1002L);
        entity.setDecisionComment(comment);
        entity.setCreatedAt(CREATED_AT);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
