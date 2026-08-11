package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.PlatformAccountEstablishmentRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台账户建立入口，只允许受权平台主体提交登录名与一次性初始口令。 */
@RestController
@RequestMapping("/api/v1/platform/accounts")
public final class PlatformAccountEstablishmentController {

    private final PlatformAccountEstablishmentApplicationService accounts;

    /** 构造平台账户建立 HTTP 入口。 */
    public PlatformAccountEstablishmentController(PlatformAccountEstablishmentApplicationService accounts) {
        this.accounts = Objects.requireNonNull(accounts, "平台账户建立服务不能为空");
    }

    @PostMapping
    public ResponseEntity<PlatformAccountSummary> establish(
            PlatformRequestContext platform, @RequestBody Map<String, Object> payload) {
        var request = PlatformAccountEstablishmentRequest.from(payload);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(accounts.establish(platform, request.loginName(), request.passwordCopy()));
        } finally {
            request.clear();
        }
    }
}
