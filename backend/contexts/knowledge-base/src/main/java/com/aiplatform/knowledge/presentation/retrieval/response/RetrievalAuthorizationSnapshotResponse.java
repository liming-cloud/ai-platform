package com.aiplatform.knowledge.presentation.retrieval.response;

import com.aiplatform.knowledge.application.retrieval.result.RetrievalAuthorizationSnapshot;
import java.util.List;

/**
 * 检索响应中的当前主体授权事实，不包含其他用户或角色的 ACL 明细。
 */
public record RetrievalAuthorizationSnapshotResponse(
        List<String> roleCodes,
        List<String> permissionCodes,
        List<String> requestedKnowledgeBaseIds,
        List<String> authorizedKnowledgeBaseIds
) {
    public static RetrievalAuthorizationSnapshotResponse from(RetrievalAuthorizationSnapshot snapshot) {
        return new RetrievalAuthorizationSnapshotResponse(
                snapshot.roleCodes(),
                snapshot.permissionCodes(),
                snapshot.requestedKnowledgeBaseIds(),
                snapshot.authorizedKnowledgeBaseIds()
        );
    }
}
