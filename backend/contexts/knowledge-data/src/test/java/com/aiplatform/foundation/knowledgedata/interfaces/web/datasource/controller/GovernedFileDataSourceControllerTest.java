package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GovernedFileDataSourceControllerTest {

    @Test
    void 探测必须委派给受控文件探测用例并返回新的生命周期事实() {
        var registration = mock(GovernedFileDataSourceRegistrationService.class);
        var probes = mock(FileDataSourceProbeService.class);
        var controller = new GovernedFileDataSourceController(registration, probes);
        var context = mock(TrustedRequestContext.class);
        var expected = new GovernedDataSourceLifecycleResult(
                "3001", "2001", GovernedDataSourceStatus.READY, 2);
        when(probes.probe(any())).thenReturn(expected);

        var result = controller.probe(context, "2001", "3001", 1);

        var command = ArgumentCaptor.forClass(
                com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeFileDataSourceCommand.class);
        verify(probes).probe(command.capture());
        assertThat(command.getValue().context()).isSameAs(context);
        assertThat(command.getValue().knowledgeSpaceId()).isEqualTo("2001");
        assertThat(command.getValue().dataSourceId()).isEqualTo("3001");
        assertThat(command.getValue().expectedVersion()).isEqualTo(1);
        assertThat(result).isSameAs(expected);
    }
}
