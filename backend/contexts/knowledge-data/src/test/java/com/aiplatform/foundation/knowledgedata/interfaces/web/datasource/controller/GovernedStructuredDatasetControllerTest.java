package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedStructuredDatasetRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.StructuredDatasetCsvSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.publication.StructuredDatasetSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.request.StructuredDatasetFieldRequest;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.request.StructuredDatasetRegistrationRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

class GovernedStructuredDatasetControllerTest {

    @Test
    void 接受浏览器上传的无字符集CSV媒体类型并返回已创建() throws Exception {
        var registration = mock(GovernedStructuredDatasetRegistrationService.class);
        var csv = mock(MultipartFile.class);
        org.mockito.Mockito.when(csv.getContentType()).thenReturn("text/csv");
        org.mockito.Mockito.when(csv.getInputStream()).thenReturn(new ByteArrayInputStream("content\n测试".getBytes()));
        var controller = new GovernedStructuredDatasetController(
                registration,
                mock(StructuredDatasetCsvSynchronizationService.class),
                mock(StructuredDatasetSyncArtifactPublicationService.class));

        var response = controller.register(context(), new StructuredDatasetRegistrationRequest(
                "202", "客服知识库", "csv-mapping-v1", "content", null,
                List.of(new StructuredDatasetFieldRequest("content", "TEXT", "PUBLIC")),
                "NON_BLANK_CONTENT"), csv);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(registration).register(any());
    }

    @Test
    void 拒绝显式声明的非UTF8CSV字符集() {
        var registration = mock(GovernedStructuredDatasetRegistrationService.class);
        var csv = mock(MultipartFile.class);
        org.mockito.Mockito.when(csv.getContentType()).thenReturn("text/csv; charset=ISO-8859-1");
        var controller = new GovernedStructuredDatasetController(
                registration,
                mock(StructuredDatasetCsvSynchronizationService.class),
                mock(StructuredDatasetSyncArtifactPublicationService.class));

        assertThrows(IllegalArgumentException.class, () -> controller.register(
                context(), definition(), csv));
    }

    @Test
    void 同步端点只转发可信上下文来源标识和预期版本() {
        var registration = mock(GovernedStructuredDatasetRegistrationService.class);
        var synchronization = mock(StructuredDatasetCsvSynchronizationService.class);
        var controller = new GovernedStructuredDatasetController(
                registration, synchronization, mock(StructuredDatasetSyncArtifactPublicationService.class));

        controller.synchronize(context(), "202", "303", 4);

        verify(synchronization).synchronize(org.mockito.ArgumentMatchers.argThat(command ->
                command.context().equals(context())
                        && command.knowledgeSpaceId().equals("202")
                        && command.dataSourceId().equals("303")
                        && command.expectedVersion() == 4));
    }

    @Test
    void 发布端点只转发可信上下文来源产物标识和预期版本() {
        var registration = mock(GovernedStructuredDatasetRegistrationService.class);
        var publication = mock(StructuredDatasetSyncArtifactPublicationService.class);
        var controller = new GovernedStructuredDatasetController(
                registration, mock(StructuredDatasetCsvSynchronizationService.class), publication);

        controller.publish(context(), "202", "303", "404", 5);

        verify(publication).publish(org.mockito.ArgumentMatchers.argThat(command ->
                command.context().equals(context())
                        && command.knowledgeSpaceId().equals("202")
                        && command.dataSourceId().equals("303")
                        && command.artifactId().equals("404")
                        && command.expectedVersion() == 5));
    }

    private static StructuredDatasetRegistrationRequest definition() {
        return new StructuredDatasetRegistrationRequest(
                "202", "客服知识库", "csv-mapping-v1", "content", null,
                List.of(new StructuredDatasetFieldRequest("content", "TEXT", "PUBLIC")),
                "NON_BLANK_CONTENT");
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new com.aiplatform.foundation.shared.kernel.tenant.TenantId(101),
                new com.aiplatform.foundation.security.identity.SubjectId(401),
                new com.aiplatform.foundation.security.identity.MembershipId(303),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE,
                1,
                Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("knowledge:write")),
                "trc-dataset-http");
    }
}
