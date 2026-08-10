package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;

/**
 * 撤销成员全部租户范围会话的安全端口，不接收浏览器 token 或平台范围身份。
 */
@FunctionalInterface
public interface TenantMemberSessionRevoker {

    void revokeTenantSessions(MemberId membershipId);
}
