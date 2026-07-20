package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.KnowledgeBase;

public interface KnowledgePermissionPolicy {
    boolean canAccess(KnowledgeBase knowledgeBase, String principalId);
}
