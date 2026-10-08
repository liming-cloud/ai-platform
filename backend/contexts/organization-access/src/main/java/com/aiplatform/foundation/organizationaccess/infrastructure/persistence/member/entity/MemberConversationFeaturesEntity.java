package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 四项功能的单表更新映射；复合主键条件和版本必须由仓储同时约束。 */
@TableName("organization_access.member_conversation_features")
public class MemberConversationFeaturesEntity {
    private Long tenantId;
    private Long membershipId;
    private Boolean assistantSelection;
    private Boolean conversation;
    private Boolean ownHistory;
    private Boolean authorizedCitations;
    private Long version;
    public Long getTenantId() { return tenantId; }
    public Long getMembershipId() { return membershipId; }
    public Boolean getAssistantSelection() { return assistantSelection; }
    public Boolean getConversation() { return conversation; }
    public Boolean getOwnHistory() { return ownHistory; }
    public Boolean getAuthorizedCitations() { return authorizedCitations; }
    public Long getVersion() { return version; }
}
