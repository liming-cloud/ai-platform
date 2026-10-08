package com.aiplatform.foundation.organizationaccess.interfaces.web.member.request;

import com.aiplatform.foundation.organizationaccess.application.member.service.MemberFeatureSettingsService.Change;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberProvisioningCommand;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 仅接受完整四布尔值和两个精确版本，不接受角色、助手集合或外部租户范围。 */
public final class MemberFeatureSettingsRequest {
    private MemberFeatureSettingsRequest() { }

    /** 缺失、未知、可空或非布尔字段均拒绝，不能隐式补授权。 */
    public static Change from(Map<String, Object> body) {
        if (body == null || !body.keySet().equals(Set.of("conversationFeatures", "expectedFeatureVersion", "expectedTenantFeatureVersion"))
                || !(body.get("conversationFeatures") instanceof Map<?, ?> values) || !values.keySet().equals(MemberProvisioningCommand.FEATURE_CODES)) {
            throw new IllegalArgumentException("成员功能字段不合法");
        }
        var features = new TreeMap<String, Boolean>();
        for (var code : MemberProvisioningCommand.FEATURE_CODES) {
            if (!(values.get(code) instanceof Boolean enabled)) throw new IllegalArgumentException("成员功能必须为布尔值");
            features.put(code, enabled);
        }
        return new Change(features, MemberProvisioningRequest.version(body.get("expectedFeatureVersion")),
                MemberProvisioningRequest.version(body.get("expectedTenantFeatureVersion")));
    }
}
