package com.aiplatform.foundation.modelruntime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerification;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModelRuntimeDomainTypeTest {

    private static final Pattern ERROR_CODE = Pattern.compile(
            "^MODEL_RUNTIME\\.(REQUEST|AUTHENTICATION|AUTHORIZATION|RESOURCE|"
                    + "CONFLICT|QUOTA|DEPENDENCY_UNAVAILABLE|DEPENDENCY_RESPONSE|SYSTEM)"
                    + "\\.\\d{4}$");

    @Test
    void 聚合与值对象应声明明确领域类型() {
        assertThat(AggregateRoot.class).isAssignableFrom(ProviderConnection.class);
        assertThat(AggregateRoot.class).isAssignableFrom(ModelDeployment.class);
        assertThat(AggregateRoot.class).isAssignableFrom(ModelCapabilityVerification.class);

        assertThat(ValueObject.class).isAssignableFrom(ProviderConnectionId.class);
        assertThat(ValueObject.class).isAssignableFrom(ModelDeploymentId.class);
        assertThat(ValueObject.class).isAssignableFrom(ModelCapabilityVerificationId.class);
        assertThat(ValueObject.class).isAssignableFrom(ModelCapabilityObservation.class);
        assertThat(ValueObject.class).isAssignableFrom(ProviderKind.class);
        assertThat(ValueObject.class).isAssignableFrom(AuthenticationType.class);
        assertThat(ValueObject.class).isAssignableFrom(CredentialReference.class);
        assertThat(ValueObject.class).isAssignableFrom(ProviderConnectionStatus.class);
        assertThat(ValueObject.class).isAssignableFrom(ModelDeploymentStatus.class);
        assertThat(ValueObject.class).isAssignableFrom(ModelCapability.class);
        assertThat(ValueObject.class).isAssignableFrom(ModelType.class);
    }

    @Test
    void 错误编码应符合上下文格式且互不重复() {
        var codes = new HashSet<String>();
        for (var error : ModelRuntimeErrorCode.values()) {
            assertThat(error.code()).matches(ERROR_CODE);
            assertThat(error.code().split("\\.", -1)[1])
                    .isEqualTo(error.type().name());
            assertThat(codes.add(error.code())).isTrue();
            assertThat(error.defaultMessage()).isNotBlank();
        }
    }
}
