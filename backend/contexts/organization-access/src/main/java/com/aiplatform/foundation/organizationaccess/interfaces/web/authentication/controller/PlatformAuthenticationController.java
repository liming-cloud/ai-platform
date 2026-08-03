package com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller;

import com.aiplatform.foundation.organizationaccess.application.authentication.command.PlatformLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PlatformAuthenticationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.request.PlatformLoginRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.response.SessionResponse;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台租户治理登录入口；只签发独立平台会话，不能提交或恢复租户成员身份。
 */
@RestController
@RequestMapping("/api/v1/platform/auth/sessions")
public class PlatformAuthenticationController {

    private final PlatformAuthenticationService authentication;
    private final EntityIdGenerator identifiers;

    public PlatformAuthenticationController(
            PlatformAuthenticationService authentication,
            EntityIdGenerator identifiers) {
        this.authentication = authentication;
        this.identifiers = identifiers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse login(@RequestBody PlatformLoginRequest request) {
        var password = request.passwordCopy();
        try (var command = new PlatformLoginCommand(
                request.loginName(),
                password,
                AccessChannel.CONSOLE,
                TraceIds.next(identifiers))) {
            var issued = authentication.authenticate(command);
            return new SessionResponse(
                    issued.tokenName(),
                    issued.tokenValue(),
                    issued.expiresIn().toSeconds());
        } finally {
            Arrays.fill(password, '\0');
            request.clearPassword();
        }
    }
}
