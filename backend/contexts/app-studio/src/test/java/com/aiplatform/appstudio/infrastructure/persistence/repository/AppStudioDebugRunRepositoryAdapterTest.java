package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunEvidenceSnapshot;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunQueryFilter;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatRetrievalEvidencePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.KbCitationRecordPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppStudioDebugRunMapper;
import com.aiplatform.appstudio.infrastructure.persistence.projection.AppStudioDebugRunDetailRow;
import com.aiplatform.appstudio.infrastructure.persistence.projection.AppStudioDebugRunSummaryRow;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AppStudioDebugRunRepositoryAdapterTest {
    @Test
    void recordPersistsEveryEvidenceSnapshotRow() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        when(mapper.insertRetrievalEvidence(any(AppChatRetrievalEvidencePersistenceEntity.class))).thenReturn(1);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);

        adapter.record(new AppStudioDebugRunSnapshot(
                TenantId.of("2001"),
                "5002",
                "trace-1",
                Instant.parse("2026-06-16T08:38:00Z"),
                List.of(new AppStudioDebugRunEvidenceSnapshot(
                        "20",
                        "18",
                        "1",
                        0.98d,
                        "VECTOR",
                        "kb-20-active-v1"
                ))
        ));

        ArgumentCaptor<AppChatRetrievalEvidencePersistenceEntity> captor = ArgumentCaptor.forClass(AppChatRetrievalEvidencePersistenceEntity.class);
        verify(mapper).insertRetrievalEvidence(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(captor.getValue().getAssistantMessageId()).isEqualTo(5002L);
        assertThat(captor.getValue().getKnowledgeBaseId()).isEqualTo(20L);
        assertThat(captor.getValue().getDocumentId()).isEqualTo(18L);
        assertThat(captor.getValue().getChunkId()).isEqualTo(1L);
        assertThat(captor.getValue().getRetrievalSignal()).isEqualTo("VECTOR");
        assertThat(captor.getValue().getIndexVersion()).isEqualTo("kb-20-active-v1");
    }

    @Test
    void listDebugRunsMapsSummaryRows() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);
        AppStudioDebugRunSummaryRow row = new AppStudioDebugRunSummaryRow();
        row.setAssistantMessageId(23L);
        row.setUserMessageId(22L);
        row.setApplicationId(11L);
        row.setSessionId(9L);
        row.setTraceId("appstudio-ui-debug-20260616-hit");
        row.setModelInvocationLogId(3L);
        row.setInvocationStatus("SUCCEEDED");
        row.setCitationCount(1);
        row.setEvidenceCount(2);
        row.setProviderCode("siliconflow");
        row.setModelCode("Qwen/Qwen2.5-7B-Instruct");
        row.setInputTokens(122);
        row.setOutputTokens(17);
        row.setLatencyMs(2155);
        row.setCreatedAt(Instant.parse("2026-06-16T08:38:00Z"));
        when(mapper.findDebugRunSummaries(2001L, 11L, 1001L, 9L, "trace-1", null, "SUCCEEDED", 50))
                .thenReturn(List.of(row));

        var results = adapter.listDebugRuns(new AppStudioDebugRunQueryFilter(
                TenantId.of("2001"),
                "11",
                "1001",
                "9",
                "trace-1",
                null,
                "SUCCEEDED",
                50
        ));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().assistantMessageId()).isEqualTo("23");
        assertThat(results.getFirst().answerable()).isTrue();
        assertThat(results.getFirst().providerCode()).isEqualTo("siliconflow");
    }

    @Test
    void listDebugRunsMarksFailedInvocationAsNotAnswerable() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);
        AppStudioDebugRunSummaryRow row = new AppStudioDebugRunSummaryRow();
        row.setAssistantMessageId(41L);
        row.setUserMessageId(40L);
        row.setApplicationId(11L);
        row.setSessionId(9L);
        row.setTraceId("model-failed-trace");
        row.setModelInvocationLogId(99L);
        row.setInvocationStatus("FAILED");
        row.setCitationCount(0);
        row.setEvidenceCount(1);
        row.setProviderCode("siliconflow");
        row.setModelCode("Qwen/Qwen2.5-7B-Instruct");
        row.setCreatedAt(Instant.parse("2026-06-21T12:00:00Z"));
        when(mapper.findDebugRunSummaries(2001L, 11L, 1001L, null, null, null, "FAILED", 50))
                .thenReturn(List.of(row));

        var results = adapter.listDebugRuns(new AppStudioDebugRunQueryFilter(
                TenantId.of("2001"),
                "11",
                "1001",
                null,
                null,
                null,
                "FAILED",
                50
        ));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().answerable()).isFalse();
        assertThat(results.getFirst().invocationStatus()).isEqualTo("FAILED");
        assertThat(results.getFirst().modelInvocationLogId()).isEqualTo("99");
    }

    @Test
    void listDebugRunsMarksRetrievalFailClosedAsNotAnswerable() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);
        AppStudioDebugRunSummaryRow row = new AppStudioDebugRunSummaryRow();
        row.setAssistantMessageId(33L);
        row.setUserMessageId(32L);
        row.setApplicationId(12L);
        row.setSessionId(13L);
        row.setTraceId("ai-main-flow-fresh-message-20260624173022");
        row.setModelInvocationLogId(null);
        row.setInvocationStatus(null);
        row.setDecisionSource("knowledge-retrieval-fail-closed");
        row.setCitationCount(0);
        row.setEvidenceCount(0);
        row.setCreatedAt(Instant.parse("2026-06-24T09:30:22Z"));
        when(mapper.findDebugRunSummaries(2001L, 12L, 1001L, 13L, null, null, null, 50))
                .thenReturn(List.of(row));

        var results = adapter.listDebugRuns(new AppStudioDebugRunQueryFilter(
                TenantId.of("2001"),
                "12",
                "1001",
                "13",
                null,
                null,
                null,
                50
        ));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().answerable()).isFalse();
        assertThat(results.getFirst().modelInvocationLogId()).isNull();
        assertThat(results.getFirst().invocationStatus()).isNull();
    }

    @Test
    void findDebugRunDetailHydratesCitationsAndEvidences() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);
        AppStudioDebugRunDetailRow row = new AppStudioDebugRunDetailRow();
        row.setAssistantMessageId(23L);
        row.setUserMessageId(22L);
        row.setApplicationId(11L);
        row.setSessionId(9L);
        row.setTraceId("appstudio-ui-debug-20260616-hit");
        row.setModelInvocationLogId(3L);
        row.setInvocationStatus("SUCCEEDED");
        row.setUserMessageContent("用户问句");
        row.setAssistantMessageContent("回答结果");
        row.setDecisionSource("spring-ai-chat-completion");
        row.setProviderCode("siliconflow");
        row.setModelCode("Qwen/Qwen2.5-7B-Instruct");
        row.setInputTokens(122);
        row.setOutputTokens(17);
        row.setLatencyMs(2155);
        row.setSourceContext("APP_STUDIO_CHAT");
        row.setSourceResourceType("AI_APPLICATION");
        row.setSourceResourceId("11");
        row.setCreatedAt(Instant.parse("2026-06-16T08:38:00Z"));
        when(mapper.findDebugRunDetail(2001L, 11L, 1001L, 23L)).thenReturn(row);
        when(mapper.findCitationRecordsByChatMessageId(2001L, 23L)).thenReturn(List.of(citationRecord()));
        when(mapper.findRetrievalEvidencesByAssistantMessageId(2001L, 23L)).thenReturn(List.of(evidenceRecord()));

        Optional<com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail> result =
                adapter.findDebugRunDetail(TenantId.of("2001"), "11", "1001", "23");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().citations()).hasSize(1);
        assertThat(result.orElseThrow().evidences()).hasSize(1);
        Citation citation = result.orElseThrow().citations().getFirst();
        assertThat(citation.knowledgeBaseId()).isEqualTo("20");
        assertThat(result.orElseThrow().decisionSource()).isEqualTo("spring-ai-chat-completion");
        assertThat(result.orElseThrow().sourceResourceId()).isEqualTo("11");
    }

    @Test
    void findDebugRunDetailAllowsUnscopedUserFilterForAuthorizedFullEvidenceView() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);

        Optional<com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail> result =
                adapter.findDebugRunDetail(TenantId.of("2001"), "11", null, "23");

        assertThat(result).isEmpty();
        verify(mapper).findDebugRunDetail(2001L, 11L, null, 23L);
    }

    @Test
    void findDebugRunDetailMarksFailClosedReasonAsFirstClassDiagnostic() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);
        AppStudioDebugRunDetailRow row = new AppStudioDebugRunDetailRow();
        row.setAssistantMessageId(31L);
        row.setUserMessageId(30L);
        row.setApplicationId(11L);
        row.setSessionId(9L);
        row.setTraceId("fail-closed-trace");
        row.setModelInvocationLogId(null);
        row.setInvocationStatus(null);
        row.setDecisionSource("knowledge-retrieval-fail-closed");
        row.setUserMessageContent("员工差旅报销上限是多少？");
        row.setAssistantMessageContent("当前未生成可回答结果：知识库证据不足。请补充报销制度上限条款。");
        row.setCreatedAt(Instant.parse("2026-06-18T08:38:00Z"));
        when(mapper.findDebugRunDetail(2001L, 11L, 1001L, 31L)).thenReturn(row);
        when(mapper.findCitationRecordsByChatMessageId(2001L, 31L)).thenReturn(List.of());
        when(mapper.findRetrievalEvidencesByAssistantMessageId(2001L, 31L)).thenReturn(List.of(evidenceRecord()));

        var result = adapter.findDebugRunDetail(TenantId.of("2001"), "11", "1001", "31");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().answerable()).isFalse();
        assertThat(result.orElseThrow().decisionSource()).isEqualTo("knowledge-retrieval-fail-closed");
        assertThat(result.orElseThrow().noAnswerReason()).isEqualTo("当前未生成可回答结果：知识库证据不足。请补充报销制度上限条款。");
    }

    @Test
    void findDebugRunDetailMarksFailedInvocationDiagnosticsAsFirstClassFields() {
        AppStudioDebugRunMapper mapper = Mockito.mock(AppStudioDebugRunMapper.class);
        AppStudioDebugRunRepositoryAdapter adapter = new AppStudioDebugRunRepositoryAdapter(mapper);
        AppStudioDebugRunDetailRow row = new AppStudioDebugRunDetailRow();
        row.setAssistantMessageId(41L);
        row.setUserMessageId(40L);
        row.setApplicationId(11L);
        row.setSessionId(9L);
        row.setTraceId("model-failed-trace");
        row.setModelInvocationLogId(99L);
        row.setInvocationStatus("FAILED");
        row.setDecisionSource("model-invocation-failed");
        row.setUserMessageContent("差旅报销规则是什么？");
        row.setAssistantMessageContent("模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。");
        row.setProviderCode("siliconflow");
        row.setModelCode("Qwen/Qwen2.5-7B-Instruct");
        row.setErrorCode("IllegalStateException");
        row.setErrorMessage("simulated model outage");
        row.setCreatedAt(Instant.parse("2026-06-21T12:00:00Z"));
        when(mapper.findDebugRunDetail(2001L, 11L, 1001L, 41L)).thenReturn(row);
        when(mapper.findCitationRecordsByChatMessageId(2001L, 41L)).thenReturn(List.of());
        when(mapper.findRetrievalEvidencesByAssistantMessageId(2001L, 41L)).thenReturn(List.of(evidenceRecord()));

        var result = adapter.findDebugRunDetail(TenantId.of("2001"), "11", "1001", "41");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().answerable()).isFalse();
        assertThat(result.orElseThrow().decisionSource()).isEqualTo("model-invocation-failed");
        assertThat(result.orElseThrow().noAnswerReason()).isEqualTo("模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。");
        assertThat(result.orElseThrow().errorCode()).isEqualTo("IllegalStateException");
        assertThat(result.orElseThrow().errorMessage()).isEqualTo("simulated model outage");
    }

    private static KbCitationRecordPersistenceEntity citationRecord() {
        KbCitationRecordPersistenceEntity entity = new KbCitationRecordPersistenceEntity();
        entity.setTenantId(2001L);
        entity.setChatMessageId(23L);
        entity.setKnowledgeBaseId(20L);
        entity.setDocumentId(18L);
        entity.setChunkId(1L);
        entity.setScore(BigDecimal.valueOf(0.88d));
        entity.setCitationText("命中片段");
        entity.setCreatedAt(Instant.parse("2026-06-16T08:38:00Z"));
        return entity;
    }

    private static AppChatRetrievalEvidencePersistenceEntity evidenceRecord() {
        AppChatRetrievalEvidencePersistenceEntity entity = new AppChatRetrievalEvidencePersistenceEntity();
        entity.setTenantId(2001L);
        entity.setAssistantMessageId(23L);
        entity.setTraceId("appstudio-ui-debug-20260616-hit");
        entity.setKnowledgeBaseId(20L);
        entity.setDocumentId(18L);
        entity.setChunkId(1L);
        entity.setScore(BigDecimal.valueOf(0.88d));
        entity.setRetrievalSignal("VECTOR");
        entity.setIndexVersion("kb-20-active-v1");
        entity.setCreatedAt(Instant.parse("2026-06-16T08:38:00Z"));
        return entity;
    }
}
