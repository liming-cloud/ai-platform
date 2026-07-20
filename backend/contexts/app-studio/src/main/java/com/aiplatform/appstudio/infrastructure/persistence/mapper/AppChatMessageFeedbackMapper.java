package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppChatMessageFeedbackMapper {
    String upsertFeedback(
            @Param("tenantId") Long tenantId,
            @Param("feedbackId") String feedbackId,
            @Param("sessionId") Long sessionId,
            @Param("assistantMessageId") Long assistantMessageId,
            @Param("userId") Long userId,
            @Param("rating") String rating,
            @Param("labelsJson") String labelsJson,
            @Param("comment") String comment,
            @Param("updatedAt") Instant updatedAt
    );
}
