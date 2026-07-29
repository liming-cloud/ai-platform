package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedFileDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceQueryService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedDataSourceLifecycleService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GovernedFileDataSourceControllerTest {

    @Test
    void 列表必须委派给受控FILE读取用例并转发页码参数() {
        var registration = mock(GovernedFileDataSourceRegistrationService.class);
        var probes = mock(FileDataSourceProbeService.class);
        var synchronizations = mock(FileDataSourceSynchronizationService.class);
        var publications = mock(FileSyncArtifactPublicationService.class);
        var lifecycle = mock(GovernedDataSourceLifecycleService.class);
        var queries = mock(GovernedFileDataSourceQueryService.class);
        var controller = new GovernedFileDataSourceController(
                registration, probes, synchronizations, publications, lifecycle, queries);
        var context = mock(TrustedRequestContext.class);
        var expected = PageResult.of(List.of(new GovernedFileDataSourceSummary(
                "3001", "2001", "门店文件", "READY", 5, null,
                "4001", "PASS", Instant.parse("2026-07-29T02:05:00Z"))), new PageQuery(2, 10), 11);
        when(queries.list(any())).thenReturn(expected);

        var result = controller.list(context, "2001", 2, 10);

        var query = ArgumentCaptor.forClass(
                com.aiplatform.foundation.knowledgedata.application.datasource.query.ListGovernedFileDataSourcesQuery.class);
        verify(queries).list(query.capture());
        assertThat(query.getValue().context()).isSameAs(context);
        assertThat(query.getValue().knowledgeSpaceId()).isEqualTo("2001");
        assertThat(query.getValue().page()).isEqualTo(new PageQuery(2, 10));
        assertThat(result).isSameAs(expected);
    }

    @Test
    void 探测必须委派给受控文件探测用例并返回新的生命周期事实() {
        var registration = mock(GovernedFileDataSourceRegistrationService.class);
        var probes = mock(FileDataSourceProbeService.class);
        var synchronizations = mock(FileDataSourceSynchronizationService.class);
        var publications = mock(FileSyncArtifactPublicationService.class);
        var lifecycle = mock(GovernedDataSourceLifecycleService.class);
        var queries = mock(GovernedFileDataSourceQueryService.class);
        var controller = new GovernedFileDataSourceController(
                registration, probes, synchronizations, publications, lifecycle, queries);
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

    @Test
    void 同步必须委派给受控文件同步用例并返回新的生命周期事实() {
        var registration = mock(GovernedFileDataSourceRegistrationService.class);
        var probes = mock(FileDataSourceProbeService.class);
        var synchronizations = mock(FileDataSourceSynchronizationService.class);
        var publications = mock(FileSyncArtifactPublicationService.class);
        var lifecycle = mock(GovernedDataSourceLifecycleService.class);
        var queries = mock(GovernedFileDataSourceQueryService.class);
        var controller = new GovernedFileDataSourceController(
                registration, probes, synchronizations, publications, lifecycle, queries);
        var context = mock(TrustedRequestContext.class);
        var expected = new GovernedDataSourceLifecycleResult(
                "3001", "2001", GovernedDataSourceStatus.READY, 4, null, "4001", QualityStatus.PASS);
        when(synchronizations.synchronize(any())).thenReturn(expected);

        var result = controller.synchronize(context, "2001", "3001", 3);

        var command = ArgumentCaptor.forClass(
                com.aiplatform.foundation.knowledgedata.application.datasource.command.SynchronizeFileDataSourceCommand.class);
        verify(synchronizations).synchronize(command.capture());
        assertThat(command.getValue().context()).isSameAs(context);
        assertThat(command.getValue().knowledgeSpaceId()).isEqualTo("2001");
        assertThat(command.getValue().dataSourceId()).isEqualTo("3001");
        assertThat(command.getValue().expectedVersion()).isEqualTo(3);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void 退役必须委派给受控生命周期用例并返回新的生命周期事实() {
        var registration = mock(GovernedFileDataSourceRegistrationService.class);
        var probes = mock(FileDataSourceProbeService.class);
        var synchronizations = mock(FileDataSourceSynchronizationService.class);
        var publications = mock(FileSyncArtifactPublicationService.class);
        var lifecycle = mock(GovernedDataSourceLifecycleService.class);
        var queries = mock(GovernedFileDataSourceQueryService.class);
        var controller = new GovernedFileDataSourceController(
                registration, probes, synchronizations, publications, lifecycle, queries);
        var context = mock(TrustedRequestContext.class);
        var expected = new GovernedDataSourceLifecycleResult(
                "3001", "2001", GovernedDataSourceStatus.RETIRED, 5);
        when(lifecycle.transition(any())).thenReturn(expected);

        var result = controller.retire(context, "2001", "3001", 4);

        var command = ArgumentCaptor.forClass(
                com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleCommand.class);
        verify(lifecycle).transition(command.capture());
        assertThat(command.getValue().context()).isSameAs(context);
        assertThat(command.getValue().knowledgeSpaceId()).isEqualTo("2001");
        assertThat(command.getValue().dataSourceId()).isEqualTo("3001");
        assertThat(command.getValue().expectedVersion()).isEqualTo(4);
        assertThat(command.getValue().action().name()).isEqualTo("RETIRE");
        assertThat(result).isSameAs(expected);
    }

    @Test
    void 发布必须委派给受控同步产物发布用例并返回新的生命周期事实() {
        var registration = mock(GovernedFileDataSourceRegistrationService.class);
        var probes = mock(FileDataSourceProbeService.class);
        var synchronizations = mock(FileDataSourceSynchronizationService.class);
        var publications = mock(FileSyncArtifactPublicationService.class);
        var lifecycle = mock(GovernedDataSourceLifecycleService.class);
        var queries = mock(GovernedFileDataSourceQueryService.class);
        var controller = new GovernedFileDataSourceController(
                registration, probes, synchronizations, publications, lifecycle, queries);
        var context = mock(TrustedRequestContext.class);
        var expected = new GovernedDataSourceLifecycleResult(
                "3001", "2001", GovernedDataSourceStatus.READY, 5, null, "4001", QualityStatus.PASS);
        when(publications.publish(any())).thenReturn(expected);

        var result = controller.publish(context, "2001", "3001", "4001", 4);

        var command = ArgumentCaptor.forClass(
                com.aiplatform.foundation.knowledgedata.application.datasource.command.PublishFileSyncArtifactCommand.class);
        verify(publications).publish(command.capture());
        assertThat(command.getValue().context()).isSameAs(context);
        assertThat(command.getValue().knowledgeSpaceId()).isEqualTo("2001");
        assertThat(command.getValue().dataSourceId()).isEqualTo("3001");
        assertThat(command.getValue().artifactId()).isEqualTo("4001");
        assertThat(command.getValue().expectedVersion()).isEqualTo(4);
        assertThat(result).isSameAs(expected);
    }
}
