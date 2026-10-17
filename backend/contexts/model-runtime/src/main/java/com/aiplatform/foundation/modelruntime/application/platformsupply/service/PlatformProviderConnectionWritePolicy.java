package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/** S1 连接命令的实例级只读边界；授权后、秘密解密和业务副作用前统一检查。 */
public record PlatformProviderConnectionWritePolicy(boolean writesAvailable) {

    /** @throws ApplicationException 本实例暂停连接写入时拒绝，不改变已有连接或发布事实。 */
    public void requireAvailable() {
        if (!writesAvailable) throw new ApplicationException(ModelRuntimeErrorCode.PLATFORM_CONNECTION_WRITES_PAUSED);
    }
}
