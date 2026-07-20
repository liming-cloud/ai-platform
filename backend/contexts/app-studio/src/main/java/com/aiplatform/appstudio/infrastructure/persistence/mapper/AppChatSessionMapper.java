package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatMessagePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatSessionPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.KbCitationRecordPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppChatSessionMapper extends BaseMapper<AppChatSessionPersistenceEntity> {
    Long nextSessionId();

    Long nextMessageId();

    int insertMessage(AppChatMessagePersistenceEntity entity);

    int insertCitationRecord(KbCitationRecordPersistenceEntity entity);

    int touchSession(@Param("tenantId") Long tenantId, @Param("sessionId") Long sessionId, @Param("updatedAt") java.time.Instant updatedAt);

    boolean existsByClientMessageId(
            @Param("tenantId") Long tenantId,
            @Param("sessionId") Long sessionId,
            @Param("clientMessageId") String clientMessageId
    );

    List<AppChatSessionPersistenceEntity> findSessions(@Param("tenantId") Long tenantId, @Param("applicationId") Long applicationId, @Param("userId") Long userId);

    List<AppChatMessagePersistenceEntity> findMessages(@Param("tenantId") Long tenantId, @Param("sessionId") Long sessionId);

    List<KbCitationRecordPersistenceEntity> findCitationRecordsByMessageIds(@Param("tenantId") Long tenantId, @Param("messageIds") List<Long> messageIds);
}
