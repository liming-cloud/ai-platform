package com.aiplatform.foundation.organizationaccess.application.member.dto;

/** 已提交命令的无秘密幂等事实；身份版本变化后不得重放旧凭据命令。 */
public record MemberAccountOperation(String operation, long membershipId, String commandFingerprint,
        long resultIdentityVersion) { }
