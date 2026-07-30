package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedObjectStorageDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class GovernedObjectStorageDataSourceControllerTest {
    @Test
    void 登记入口只转发范围标识和选择修订并返回创建状态() {
        var registration = mock(GovernedObjectStorageDataSourceRegistrationService.class);
        var controller = new GovernedObjectStorageDataSourceController(registration);
        var context = mock(TrustedRequestContext.class);
        var expected = new GovernedDataSourceLifecycleResult("3001", "2001", GovernedDataSourceStatus.DRAFT, 0);
        when(registration.register(any())).thenReturn(expected);

        var response = controller.register(context, "2001", "平台对象来源", "rng_7c90d7a8716e4a79", 7);

        var command = ArgumentCaptor.forClass(RegisterObjectStorageDataSourceCommand.class);
        verify(registration).register(command.capture());
        assertThat(command.getValue().context()).isSameAs(context);
        assertThat(command.getValue().knowledgeSpaceId()).isEqualTo("2001");
        assertThat(command.getValue().rangeId()).isEqualTo("rng_7c90d7a8716e4a79");
        assertThat(command.getValue().selectionRevision()).isEqualTo(7);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }
}
