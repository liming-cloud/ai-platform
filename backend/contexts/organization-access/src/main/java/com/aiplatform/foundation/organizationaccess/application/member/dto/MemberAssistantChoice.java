package com.aiplatform.foundation.organizationaccess.application.member.dto;

/** 开户选择实际已发布助手的无秘密投影，不泄露其他上下文内部对象。 */
public record MemberAssistantChoice(String assistantId, String displayName, String assistantVersionId, int versionNumber) {}
