package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.modelhub.infrastructure.persistence.entity.ModelCreationRequestPersistenceEntity;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ModelCreationRequestMapperXmlTest {
    private static final String RESOURCE = "mapper/modelhub/ModelCreationRequestMapper.xml";

    @Test
    void claimUsesTheTenantResourceTypeAndRequestIdAsOneAtomicConflictKey() throws Exception {
        ModelCreationRequestPersistenceEntity request = new ModelCreationRequestPersistenceEntity();
        request.setTenantId(2001L);
        request.setResourceType("MODEL_PROVIDER");
        request.setRequestId("provider-create-001");
        request.setRequestDigest("a".repeat(64));
        MappedStatement statement = statement("insertClaim");
        String sql = normalized(statement.getBoundSql(request).getSql());

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(statement.isFlushCacheRequired()).isTrue();
        assertThat(sql)
                .contains("insert into mdl_creation_request")
                .contains("on conflict (tenant_id, resource_type, request_id) do nothing")
                .contains("returning id");
    }

    @Test
    void replayLookupAndCompletionRemainTenantScopedAndDigestFenced() throws Exception {
        String lookup = normalized(statement("findClaim").getBoundSql(Map.of(
                "tenantId", 2001L,
                "resourceType", "MODEL_PROVIDER",
                "requestId", "provider-create-001"
        )).getSql());
        ModelCreationRequestPersistenceEntity completionRequest = new ModelCreationRequestPersistenceEntity();
        completionRequest.setTenantId(2001L);
        completionRequest.setResourceType("MODEL_PROVIDER");
        completionRequest.setRequestId("provider-create-001");
        completionRequest.setRequestDigest("a".repeat(64));
        completionRequest.setResultResourceId(9001L);
        completionRequest.setResultStatus("PENDING_APPROVAL");
        completionRequest.setResultApprovalRequestId("REQ-9001");
        String completion = normalized(statement("completeClaim").getBoundSql(completionRequest).getSql());

        assertThat(lookup)
                .contains("tenant_id = ?")
                .contains("resource_type = ?")
                .contains("request_id = ?")
                .contains("result_parent_resource_id")
                .contains("result_resource_code")
                .contains("result_model_type")
                .contains("result_status")
                .contains("result_approval_request_id")
                .contains("result_connectivity_status");
        assertThat(completion)
                .contains("request_digest = ?")
                .contains("result_parent_resource_id = ?")
                .contains("result_resource_code = ?")
                .contains("result_model_type = ?")
                .contains("result_status = ?")
                .contains("result_approval_request_id = ?")
                .contains("result_connectivity_status = ?")
                .contains("result_resource_id is null")
                .contains("completed_at is null");
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ModelCreationRequestMapper.class.getName() + "." + id);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
