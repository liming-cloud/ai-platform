package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.modelhub.infrastructure.messaging.inbox.ApprovalDecisionInboxReceipt;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApprovalDecisionInboxMapperXmlTest {
    private static final String RESOURCE = "mapper/modelhub/ApprovalDecisionInboxMapper.xml";

    @Test
    void oneUniqueEventReceiptFencesAllDownstreamStateAndOutboxMutations() throws Exception {
        MappedStatement statement = statement("insertReceipt");
        String sql = statement.getBoundSql(Map.of("receipt", receipt()))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();

        assertThat(sql)
                .contains("insert into mdl_approval_decision_inbox")
                .contains("event_id")
                .contains("approval_request_id")
                .contains("on conflict (event_id) do nothing");
    }

    @Test
    void legacyRequestResolutionIsFencedByEveryAuthoritativeFact() throws Exception {
        String sql = statement("resolveLegacyApprovalRequestId").getBoundSql(Map.of(
                        "tenantId", 2001L,
                        "taskId", 9001L,
                        "sourceContext", "model-hub",
                        "sourceResourceType", "model_provider",
                        "sourceResourceId", 3001L
                ))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();

        assertThat(sql)
                .contains("from appr_task")
                .contains("tenant_id = ?")
                .contains("id = ?")
                .contains("source_context = ?")
                .contains("source_resource_type = ?")
                .contains("source_resource_id = ?")
                .contains("deleted_at is null");
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ApprovalDecisionInboxMapper.class.getName() + "." + id);
    }

    private static ApprovalDecisionInboxReceipt receipt() {
        return new ApprovalDecisionInboxReceipt(
                "ApprovalDecisionRecorded:2001:9001:APPROVED",
                "ApprovalDecisionRecorded",
                2001L,
                9001L,
                "REQ-9001",
                "model_provider",
                3001L,
                "trace-approval-9001",
                Instant.parse("2026-07-18T08:00:00Z")
        );
    }
}
