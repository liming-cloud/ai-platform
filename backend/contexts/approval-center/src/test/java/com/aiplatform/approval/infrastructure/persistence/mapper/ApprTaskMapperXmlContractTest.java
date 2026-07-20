package com.aiplatform.approval.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.approval.domain.model.ApprovalCancellationRequest;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprTaskPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApprTaskMapperXmlContractTest {
    private static final String RESOURCE = "mapper/approval/ApprTaskMapper.xml";

    @Test
    void requestIdentityUsesOneAtomicFullLifecycleUpsertAndReturnsOnlyMatchingFacts() throws Exception {
        String sql = normalized(statement("upsertPending").getBoundSql(Map.of("entity", pending())).getSql());

        assertThat(sql)
                .contains("insert into appr_task")
                .contains("approval_request_id")
                .contains("on conflict (tenant_id, approval_request_id)")
                .contains("do update set id = appr_task.id")
                .contains("where appr_task.approval_type = excluded.approval_type")
                .contains("and appr_task.source_context = excluded.source_context")
                .contains("and appr_task.source_resource_type = excluded.source_resource_type")
                .contains("and appr_task.source_resource_id = excluded.source_resource_id")
                .contains("and appr_task.title = excluded.title")
                .contains("and appr_task.risk_level = excluded.risk_level")
                .contains("and appr_task.requester_id = excluded.requester_id")
                .contains("and appr_task.trace_id = excluded.trace_id")
                .contains("returning id");
    }

    @Test
    void decisionIsOneTenantStatusAndVersionFencedCas() throws Exception {
        String sql = normalized(statement("decidePending").getBoundSql(Map.of(
                "tenantId", 2001L,
                "taskId", 7001L,
                "expectedVersion", 0,
                "targetStatus", "APPROVED",
                "approverId", 1002L,
                "decisionAt", Instant.parse("2026-07-18T08:00:00Z")
        )).getSql());

        assertThat(sql)
                .contains("update appr_task")
                .contains("version = version + 1")
                .contains("where tenant_id = ?")
                .contains("and id = ?")
                .contains("and status = 'pending'")
                .contains("and version = ?")
                .contains("and deleted_at is null");
    }

    @Test
    void cancellationUsesDurableInboxAndCompetesWithDecisionOnPendingStatus() throws Exception {
        ApprovalCancellationRequest cancellation = new ApprovalCancellationRequest(
                TenantId.of("2001"),
                "ModelApprovalCancellationRequested:2001:model_provider:REQ-1001",
                "REQ-1001",
                "model-hub",
                "model_provider",
                "3001",
                "1002",
                "地址录入错误",
                "TRACE-CANCEL-001",
                Instant.parse("2026-07-21T08:00:00Z")
        );
        String upsert = normalized(statement("upsertCancellation")
                .getBoundSql(Map.of("cancellation", cancellation)).getSql());
        String reconcile = normalized(statement("reconcileCancellation").getBoundSql(Map.of(
                "tenantId", 2001L,
                "approvalRequestId", "REQ-1001"
        )).getSql());
        String terminal = normalized(statement("markCancellationAfterTerminalDecision").getBoundSql(Map.of(
                "tenantId", 2001L,
                "approvalRequestId", "REQ-1001"
        )).getSql());

        assertThat(upsert)
                .contains("insert into appr_model_approval_cancellation")
                .contains("on conflict (tenant_id, approval_request_id)")
                .contains("where appr_model_approval_cancellation.event_id = excluded.event_id")
                .contains("returning id");
        assertThat(reconcile)
                .contains("for update")
                .contains("update appr_task task")
                .contains("set status = 'cancelled'")
                .contains("and task.status = 'pending'")
                .contains("version = task.version + 1")
                .contains("insert into appr_record")
                .contains("'cancel'");
        assertThat(terminal)
                .contains("status = case when task.status = 'cancelled' then 'applied' else 'decision_won' end")
                .contains("and task.status <> 'pending'");
    }

    @Test
    void requestAndCancellationFirstWritesShareOneTransactionScopedCoordinationLock() throws Exception {
        String sql = normalized(statement("lockApprovalRequest").getBoundSql(Map.of(
                "tenantId", 2001L,
                "approvalRequestId", "REQ-1001"
        )).getSql());

        assertThat(sql)
                .contains("pg_advisory_xact_lock")
                .contains("hashtextextended")
                .contains("?::text || ':' || ?")
                .doesNotContain("pg_advisory_lock(");
    }

    @Test
    void countAndPageShareAllTenantFiltersAndPageReadsAreDatabaseBounded() throws Exception {
        ApprovalTaskQuery query = new ApprovalTaskQuery(
                TenantId.of("2001"),
                ApprovalStatus.PENDING,
                ApprovalRiskLevel.HIGH_RISK,
                "model-hub",
                "100%_供应商",
                5000,
                20
        );
        Map<String, Object> parameters = Map.of("query", query);
        String count = normalized(statement("countTasks").getBoundSql(parameters).getSql());
        String page = normalized(statement("findTasks").getBoundSql(parameters).getSql());

        for (String sql : java.util.List.of(count, page)) {
            assertThat(sql)
                    .contains("tenant_id = cast(? as bigint)")
                    .contains("status = ?")
                    .contains("risk_level = ?")
                    .contains("lower(source_context) = ?")
                    .contains("lower(title) like ?")
                    .contains("cast(source_resource_id as text) like ?");
        }
        assertThat(page)
                .contains("order by created_at desc, id desc")
                .contains("limit ?")
                .contains("offset ?");
        assertThat(query.offset()).isEqualTo(99_980L);
        assertThat(query.keywordPattern()).isEqualTo("%100\\%\\_供应商%");
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ApprTaskMapper.class.getName() + "." + id);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static ApprTaskPersistenceEntity pending() {
        ApprTaskPersistenceEntity entity = new ApprTaskPersistenceEntity();
        entity.setTenantId(2001L);
        entity.setApprovalRequestId("REQ-1001");
        entity.setApprovalType("model_config_change");
        entity.setSourceContext("model-hub");
        entity.setSourceResourceType("model_provider");
        entity.setSourceResourceId(3001L);
        entity.setTitle("创建供应商");
        entity.setRiskLevel("HIGH_RISK");
        entity.setRequesterId(1001L);
        entity.setTraceId("trace-approval-1");
        entity.setCreatedAt(Instant.parse("2026-07-18T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-07-18T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        return entity;
    }
}
