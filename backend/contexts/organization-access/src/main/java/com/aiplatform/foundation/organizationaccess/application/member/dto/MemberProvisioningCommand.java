package com.aiplatform.foundation.organizationaccess.application.member.dto;

import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import java.util.Objects;
import java.util.Set;
import java.util.Map;

/** 非秘密开户命令；密码独立传递，不能参加字符串表示或幂等指纹。 */
public record MemberProvisioningCommand(String loginName, Map<String, Boolean> features, Set<Long> assistantIds,
        long expectedTenantFeatureVersion, long expectedAuthorizationRevision) {
    public static final Set<String> FEATURE_CODES = ConversationFeatures.CODES;
    public MemberProvisioningCommand {
        if (loginName == null || !loginName.matches("[a-z][a-z0-9._-]{2,63}")) {
            throw new IllegalArgumentException("登录名格式不正确");
        }
        features = new ConversationFeatures(features).values();
        assistantIds = Set.copyOf(Objects.requireNonNull(assistantIds, "助手集合不能为空"));
        if (assistantIds.size() > 200 || assistantIds.stream().anyMatch(id -> id < 1)
                || expectedTenantFeatureVersion < 0 || expectedAuthorizationRevision < 0) {
            throw new IllegalArgumentException("成员开户授权和版本不合法");
        }
    }
}
