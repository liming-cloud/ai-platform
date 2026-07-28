package com.aiplatform.foundation.modelruntime.application.configuration.facade;

import com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Optional;

/**
 * 向其他限界上下文提供当前主体可调用模型配置的最小安全快照。
 */
public interface UsableModelConfigurationLookup {

    /**
     * 只在配置可被当前主体调用且目录能力匹配时返回冻结快照。
     */
    Optional<UsableModelConfigurationSnapshot> find(
            TrustedRequestContext context,
            ModelConfigurationId configurationId,
            ModelType requiredType);
}
