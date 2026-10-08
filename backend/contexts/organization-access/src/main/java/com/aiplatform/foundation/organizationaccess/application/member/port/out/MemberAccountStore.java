package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountOperation;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;
import java.util.Set;

/** 成员配置、单一助手授权和命令审计的组织持久化端口。 */
public interface MemberAccountStore extends MemberAccessFacts {
    /** 锁定租户治理行，再锁主体及资格，序列化同租户成员命令。 */
    void lockTenant(TenantId tenantId);
    /** 锁定与租户功能上限共行的独立授权 revision。 */
    long lockAuthorization(TenantId tenantId);
    long authorizationRevision(TenantId tenantId);
    Optional<MemberAccountOperation> operation(TenantId tenantId, long actorSubjectId, String key);
    /** 一次写入初始功能和助手关系；失败必须由外层业务事务回滚。 */
    void initialize(TenantId tenantId, long membershipId, ConversationFeatures features, Set<Long> assistants);
    /** 仅在对象授权集合发生变化时推进租户 revision。 */
    void advanceAuthorization(TenantId tenantId, long expectedRevision);
    /** 审计与幂等结果同业务提交，不记录密码或请求正文。 */
    void record(TenantId tenantId, long actorSubjectId, String key, MemberAccountOperation operation);
}
