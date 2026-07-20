package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatRetrievalEvidencePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.KbCitationRecordPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.projection.AppStudioDebugRunDetailRow;
import com.aiplatform.appstudio.infrastructure.persistence.projection.AppStudioDebugRunSummaryRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppStudioDebugRunMapper {
    int insertRetrievalEvidence(AppChatRetrievalEvidencePersistenceEntity entity);

    List<AppStudioDebugRunSummaryRow> findDebugRunSummaries(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("traceId") String traceId,
            @Param("modelInvocationLogId") Long modelInvocationLogId,
            @Param("invocationStatus") String invocationStatus,
            @Param("limit") int limit
    );

    AppStudioDebugRunDetailRow findDebugRunDetail(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("userId") Long userId,
            @Param("assistantMessageId") Long assistantMessageId
    );

    List<KbCitationRecordPersistenceEntity> findCitationRecordsByChatMessageId(
            @Param("tenantId") Long tenantId,
            @Param("chatMessageId") Long chatMessageId
    );

    List<AppChatRetrievalEvidencePersistenceEntity> findRetrievalEvidencesByAssistantMessageId(
            @Param("tenantId") Long tenantId,
            @Param("assistantMessageId") Long assistantMessageId
    );
}
