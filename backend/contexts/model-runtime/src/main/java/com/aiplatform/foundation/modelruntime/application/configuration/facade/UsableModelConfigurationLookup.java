package com.aiplatform.foundation.modelruntime.application.configuration.facade;

import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationUsageContext;
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

    /**
     * 供持久化异步作业按原创建成员重新校验配置使用权；不得由外部请求直接构造。
     */
    default Optional<UsableModelConfigurationSnapshot> find(
            ModelConfigurationUsageContext context,
            ModelConfigurationId configurationId,
            ModelType requiredType) {
        throw new UnsupportedOperationException("当前模型配置查询不支持异步使用上下文");
    }
}
