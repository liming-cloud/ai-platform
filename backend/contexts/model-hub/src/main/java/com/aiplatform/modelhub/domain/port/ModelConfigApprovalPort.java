package com.aiplatform.modelhub.domain.port;

import com.aiplatform.modelhub.domain.ModelApprovalCancellation;
import com.aiplatform.modelhub.domain.ModelConfigChange;

public interface ModelConfigApprovalPort {
    String requestApproval(ModelConfigChange change);

    default void cancelApproval(ModelApprovalCancellation cancellation) {
        throw new UnsupportedOperationException("模型审批撤销端口未实现");
    }
}
