package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.result.ApplicationAccessMode;
import com.aiplatform.appstudio.application.result.ApplicationCapabilitiesResult;

record ApplicationAccessDecision(
        ApplicationAccessMode accessMode,
        ApplicationCapabilitiesResult capabilities
) {
}
