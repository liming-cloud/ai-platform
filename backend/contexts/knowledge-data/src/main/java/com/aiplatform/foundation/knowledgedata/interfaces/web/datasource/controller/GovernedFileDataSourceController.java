package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentProcessingException;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FILE 受治理来源登记入口；对象位置只由平台生成。 */
@RestController
@RequestMapping("/api/v1/governed-data-sources")
public class GovernedFileDataSourceController {
    private final GovernedFileDataSourceRegistrationService registration;
    public GovernedFileDataSourceController(GovernedFileDataSourceRegistrationService registration) {
        this.registration = Objects.requireNonNull(registration, "文件来源登记用例不能为空");
    }
    @PostMapping(value = "/files", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<GovernedDataSourceLifecycleResult> register(
            TrustedRequestContext context, @RequestParam String knowledgeSpaceId, @RequestParam String name,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String mediaType,
            HttpServletRequest request) {
        requireUtf8Text(mediaType);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(
                    new RegisterFileDataSourceCommand(context, knowledgeSpaceId, name, request.getInputStream())));
        } catch (IOException exception) { throw new TextContentProcessingException(exception); }
    }
    private static void requireUtf8Text(String value) {
        if (value == null) throw new IllegalArgumentException("必须提供媒体类型");
        var parsed = MediaType.parseMediaType(value);
        if (!MediaType.TEXT_PLAIN.includes(parsed) || !StandardCharsets.UTF_8.equals(parsed.getCharset())
                || parsed.getParameters().size() != 1) throw new IllegalArgumentException("只支持 UTF-8 纯文本");
    }
}
