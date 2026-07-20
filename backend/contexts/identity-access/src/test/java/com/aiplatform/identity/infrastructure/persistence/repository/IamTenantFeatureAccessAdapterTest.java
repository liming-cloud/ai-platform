package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class IamTenantFeatureAccessAdapterTest {
    @Test
    void readsTenantFeatureFromIamMapper() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countTenantFeatures(2001L, List.of("app-studio"))).thenReturn(1);
        IamTenantFeatureAccessAdapter adapter = new IamTenantFeatureAccessAdapter(mapper);

        assertThat(adapter.isFeatureEnabled(TenantId.of("2001"), " app-studio ")).isTrue();
        verify(mapper).countTenantFeatures(2001L, List.of("app-studio"));
    }

    @Test
    void returnsFalseWhenFeatureIsMissing() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countTenantFeatures(2001L, List.of("knowledge-base"))).thenReturn(0);
        IamTenantFeatureAccessAdapter adapter = new IamTenantFeatureAccessAdapter(mapper);

        assertThat(adapter.isFeatureEnabled(TenantId.of("2001"), "knowledge-base")).isFalse();
    }
}
