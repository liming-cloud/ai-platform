package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedObjectStorageDataSourceRegistrationService;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OBJECT_STORAGE 受治理来源登记入口；只接收平台签发的范围选择。 */
@RestController
@ConditionalOnBean(GovernedObjectStorageDataSourceRegistrationService.class)
@RequestMapping("/api/v1/governed-data-sources/object-storage")
public class GovernedObjectStorageDataSourceController {
    private final GovernedObjectStorageDataSourceRegistrationService registration;

    public GovernedObjectStorageDataSourceController(GovernedObjectStorageDataSourceRegistrationService registration) {
        this.registration = Objects.requireNonNull(registration, "对象存储来源登记用例不能为空");
    }

    /**
     * 不接收端点、桶名、对象键、对象正文或凭据；对象集合只能由部署受控范围解析器返回。
     */
    @PostMapping
    public ResponseEntity<GovernedDataSourceLifecycleResult> register(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @RequestParam String name,
            @RequestParam String rangeId,
            @RequestParam long selectionRevision) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(
                new RegisterObjectStorageDataSourceCommand(
                        context, knowledgeSpaceId, name, rangeId, selectionRevision)));
    }
}
