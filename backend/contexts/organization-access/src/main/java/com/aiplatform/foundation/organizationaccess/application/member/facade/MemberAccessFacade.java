package com.aiplatform.foundation.organizationaccess.application.member.facade;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;

/** 组织拥有的实时访问事实入口；调用方不能以签发时角色或前端选择替代。 */
public interface MemberAccessFacade {
    /** 读取当前身份、租户、固定资格与功能交集；缺失或失效时拒绝，禁止回退历史会话授权。 */
    MemberEffectiveAccess read(TrustedRequestContext context);
    /** 仅接受已定义的对话功能，要求当前有效交集开放该功能。 */
    void requireFeature(TrustedRequestContext context, String feature);
    /** 发布版本必须属于当前租户且仍在成员对象范围内，适用于直接访问和输出复核。 */
    void requireAssistantVersion(TrustedRequestContext context, long versionId, String feature);
}
