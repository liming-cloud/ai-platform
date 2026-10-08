package com.aiplatform.foundation.assistantdelivery.application.assistant.facade;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 向授权所有者提供当前发布与永久知识库关系，不暴露模型配置或系统提示词。 */
public interface PublishedAssistantAuthorizationLookup {
    /** 当前发布事实；缺失或未发布返回空。 */
    Optional<Fact> find(TenantId tenantId, long assistantId);
    /** 加入调用方事务，锁住与助手发布相同的行；调用方按助手标识顺序申请。 */
    Optional<Fact> lock(TenantId tenantId, long assistantId);
    /** 只包含授权和展示必需的无秘密拥有者事实。 */
    record Fact(long assistantId, String displayName, long assistantVersionId, int versionNumber, long knowledgeSpaceId) { }
}
