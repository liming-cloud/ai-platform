package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat;

import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType;
import java.util.Objects;

/**
 * 已冻结且仍可用的聊天路由候选，不包含凭据或供应商实现。
 */
public record GovernedChatRouteCandidate(UsableModelConfigurationSnapshot configuration) {

    public GovernedChatRouteCandidate {
        configuration = Objects.requireNonNull(configuration, "聊天候选配置不能为空");
        if (configuration.modelType() != ModelType.CHAT) {
            throw new IllegalArgumentException("聊天路由候选必须使用 Chat 配置");
        }
    }
}
