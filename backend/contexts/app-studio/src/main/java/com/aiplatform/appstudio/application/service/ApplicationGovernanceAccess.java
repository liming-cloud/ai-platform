package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.result.ApplicationAccessMode;
import com.aiplatform.appstudio.domain.AiApplication;

record ApplicationGovernanceAccess(
        AiApplication application,
        ApplicationAccessMode accessMode
) {
    boolean governanceReadOnly() {
        return accessMode == ApplicationAccessMode.GOVERNANCE_READ_ONLY;
    }
}
