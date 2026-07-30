package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedStructuredDatasetRegistrationService;
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
    void 只转发私有CSV流和冻结字段映射并返回已创建() throws Exception {
        var registration = mock(GovernedStructuredDatasetRegistrationService.class);
        var csv = mock(MultipartFile.class);
        org.mockito.Mockito.when(csv.getContentType()).thenReturn("text/csv;charset=UTF-8");
        org.mockito.Mockito.when(csv.getInputStream()).thenReturn(new ByteArrayInputStream("content\n测试".getBytes()));
        var controller = new GovernedStructuredDatasetController(registration);

        var response = controller.register(context(), new StructuredDatasetRegistrationRequest(
                "202", "客服知识库", "csv-mapping-v1", "content", null,
                List.of(new StructuredDatasetFieldRequest("content", "TEXT", "PUBLIC")),
                "NON_BLANK_CONTENT"), csv);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(registration).register(any());
    }

    @Test
    void 拒绝非UTF8CSV媒体类型() {
        var registration = mock(GovernedStructuredDatasetRegistrationService.class);
        var csv = mock(MultipartFile.class);
        org.mockito.Mockito.when(csv.getContentType()).thenReturn("text/plain; charset=utf-8");
        var controller = new GovernedStructuredDatasetController(registration);

        assertThrows(IllegalArgumentException.class, () -> controller.register(
                context(), definition(), csv));
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
