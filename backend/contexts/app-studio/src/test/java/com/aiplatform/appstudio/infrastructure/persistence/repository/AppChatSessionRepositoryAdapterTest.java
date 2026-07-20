package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatMessagePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatSessionPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.KbCitationRecordPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppChatSessionMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AppChatSessionRepositoryAdapterTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-06-11T01:51:49.498157Z");

    @Test
    void savePersistsSessionTitle() {
        AppChatSessionMapper mapper = Mockito.mock(AppChatSessionMapper.class);
        AppChatSessionRepositoryAdapter repository = new AppChatSessionRepositoryAdapter(mapper);
        when(mapper.insert(any(AppChatSessionPersistenceEntity.class))).thenReturn(1);

        repository.save(session());

        ArgumentCaptor<AppChatSessionPersistenceEntity> captor = ArgumentCaptor.forClass(AppChatSessionPersistenceEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("制度问答会话");
        assertThat(captor.getValue().getReleaseId()).isEqualTo(44L);
        assertThat(captor.getValue().getReleaseVersion()).isEqualTo(3L);
    }

    @Test
    void activePersistenceEntityFailsClosedWhenItsReleasePinIsMissing() {
        AppChatSessionPersistenceEntity entity = new AppChatSessionPersistenceEntity();

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ACTIVE 聊天会话缺少发布版本固定信息");
    }

    @Test
    void appendMessagePersistsCitationRecordsAlongsideChatMessage() {
        AppChatSessionMapper mapper = Mockito.mock(AppChatSessionMapper.class);
        AppChatSessionRepositoryAdapter repository = new AppChatSessionRepositoryAdapter(mapper);
        when(mapper.selectOne(any())).thenReturn(AppChatSessionPersistenceEntity.fromDomain(session()));
        when(mapper.insertMessage(any())).thenReturn(1);
        when(mapper.insertCitationRecord(any())).thenReturn(1);
        when(mapper.touchSession(any(), any(), any())).thenReturn(1);

        ChatMessage message = assistantMessageWithCitation();

        ChatMessage saved = repository.appendMessage(message);

        assertThat(saved).isEqualTo(message);
        ArgumentCaptor<AppChatMessagePersistenceEntity> messageCaptor = ArgumentCaptor.forClass(AppChatMessagePersistenceEntity.class);
        verify(mapper).insertMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getCitationCount()).isEqualTo(1);
        assertThat(messageCaptor.getValue().getModelInvocationLogId()).isEqualTo(88L);

        ArgumentCaptor<KbCitationRecordPersistenceEntity> citationCaptor = ArgumentCaptor.forClass(KbCitationRecordPersistenceEntity.class);
        verify(mapper, times(1)).insertCitationRecord(citationCaptor.capture());
        assertThat(citationCaptor.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(citationCaptor.getValue().getChatMessageId()).isEqualTo(21L);
        assertThat(citationCaptor.getValue().getKnowledgeBaseId()).isEqualTo(21L);
        assertThat(citationCaptor.getValue().getDocumentId()).isEqualTo(18L);
        assertThat(citationCaptor.getValue().getChunkId()).isEqualTo(19L);
        assertThat(citationCaptor.getValue().getCitationText()).isEqualTo("制度内容");
        assertThat(citationCaptor.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void appendMessageFailsWhenCitationRecordPersistenceReturnsZero() {
        AppChatSessionMapper mapper = Mockito.mock(AppChatSessionMapper.class);
        AppChatSessionRepositoryAdapter repository = new AppChatSessionRepositoryAdapter(mapper);
        when(mapper.selectOne(any())).thenReturn(AppChatSessionPersistenceEntity.fromDomain(session()));
        when(mapper.insertMessage(any())).thenReturn(1);
        when(mapper.insertCitationRecord(any())).thenReturn(0);
        when(mapper.touchSession(any(), any(), any())).thenReturn(1);

        assertThatThrownBy(() -> repository.appendMessage(assistantMessageWithCitation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("聊天引用记录保存失败");
    }

    @Test
    void listMessagesHydratesCitationRecordsBackIntoHistory() {
        AppChatSessionMapper mapper = Mockito.mock(AppChatSessionMapper.class);
        AppChatSessionRepositoryAdapter repository = new AppChatSessionRepositoryAdapter(mapper);
        AppChatMessagePersistenceEntity assistant = AppChatMessagePersistenceEntity.fromDomain(
                assistantMessageWithCitation(), "4", "1001");
        when(mapper.findMessages(2001L, 4L)).thenReturn(List.of(assistant));

        KbCitationRecordPersistenceEntity citationRecord = new KbCitationRecordPersistenceEntity();
        citationRecord.setTenantId(2001L);
        citationRecord.setChatMessageId(21L);
        citationRecord.setKnowledgeBaseId(21L);
        citationRecord.setDocumentId(18L);
        citationRecord.setChunkId(19L);
        citationRecord.setScore(java.math.BigDecimal.valueOf(0.835493));
        citationRecord.setCitationText("制度内容");
        citationRecord.setCreatedAt(NOW);
        when(mapper.findCitationRecordsByMessageIds(2001L, List.of(21L))).thenReturn(List.of(citationRecord));

        List<ChatMessage> messages = repository.listMessages(TenantId.of("2001"), "4");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).modelInvocationLogId()).isEqualTo("88");
        assertThat(messages.get(0).citations()).hasSize(1);
        assertThat(messages.get(0).citations().get(0).fragment()).isEqualTo("制度内容");
    }

    private static ChatSession session() {
        return new ChatSession(TENANT_ID, "4", "4", "44", 3, "1001", "制度问答会话", NOW, NOW);
    }

    private static ChatMessage assistantMessageWithCitation() {
        Citation citation = new Citation(
                "21",
                "18",
                "19",
                "员工手册-finalreal20260609231920.txt",
                "制度内容",
                "s3://aip-knowledge/tenants/2001/knowledge-bases/21/documents/18/chunks/19",
                0.835493
        );
        return new ChatMessage(
                TENANT_ID,
                "21",
                "4",
                ChatRole.ASSISTANT,
                "根据提供的信息，我没有找到直接引用的知识“制度内容”。请提供更多相关信息以便我能准确回答。",
                List.of(citation),
                "88",
                NOW
        );
    }
}
