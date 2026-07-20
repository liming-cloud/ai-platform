package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AppChatMessageFeedbackMapperXmlTest {
    private static final String RESOURCE = "mapper/appstudio/AppChatMessageFeedbackMapper.xml";
    private static final String STATEMENT = AppChatMessageFeedbackMapper.class.getName() + ".upsertFeedback";

    @Test
    void scopedUpsertReturnsCanonicalIdAndVerifiesTenantSessionUserAndAssistantMessage() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }

        MappedStatement statement = configuration.getMappedStatement(STATEMENT);
        String sql = statement.getBoundSql(Map.of(
                "tenantId", 2001L,
                "feedbackId", "temporary-feedback-id",
                "sessionId", 4001L,
                "assistantMessageId", 5002L,
                "userId", 1001L,
                "rating", "HELPFUL",
                "labelsJson", "[]",
                "comment", "已解决问题",
                "updatedAt", "2026-07-10T00:00:00Z"
        )).getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(statement.isFlushCacheRequired()).isTrue();
        assertThat(sql)
                .contains("from app_chat_session session")
                .contains("join app_chat_message message")
                .contains("message.tenant_id = session.tenant_id")
                .contains("message.session_id = session.id")
                .contains("message.role = 'assistant'")
                .contains("session.tenant_id =")
                .contains("session.user_id =")
                .contains("on conflict (tenant_id, assistant_message_id, user_id)")
                .contains("app_chat_message_feedback.session_id = excluded.session_id")
                .endsWith("returning feedback_id");
    }
}
