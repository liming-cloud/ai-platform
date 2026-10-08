package com.aiplatform.foundation.organizationaccess.domain.member.model;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 普通成员的四项显式对话授权；租户开放上限不能替代成员授予。 */
public record ConversationFeatures(Map<String, Boolean> values) {
    public static final Set<String> CODES = Set.of("assistantSelection", "conversation", "ownHistory", "authorizedCitations");

    /** 拒绝缺失、未知或可空功能，持久化恢复也不能补默认授权。 */
    public ConversationFeatures {
        values = Map.copyOf(Objects.requireNonNull(values, "成员功能不能为空"));
        if (!values.keySet().equals(CODES)) throw new IllegalArgumentException("成员功能必须包含全部四项");
    }

    /** 新开户不能授予租户尚未开放的功能。 */
    public void requireWithin(TenantFeatureSettings ceiling) {
        for (var code : CODES) {
            if (values.get(code) && !ceiling.allows(TenantFeature.fromCode(code))) {
                throw new IllegalArgumentException("成员功能超出租户开放范围");
            }
        }
    }

    /** 有效值取保存事实与当前租户上限的交集。 */
    public boolean allows(String code, TenantFeatureSettings ceiling) {
        if (!CODES.contains(code)) throw new IllegalArgumentException("成员功能代码不合法");
        return values.get(code) && ceiling.allows(TenantFeature.fromCode(code));
    }
}
