package com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.command.PersonLoginCommand;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.request.LoginRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.response.SessionResponse;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.web.BearerTokens;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员登录和当前会话注销入口。
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
public class AuthenticationController {

    private final PersonAuthenticationService authentication;
    private final AuthenticatedSessionGateway sessions;
    private final EntityIdGenerator identifiers;

    public AuthenticationController(
            PersonAuthenticationService authentication,
            AuthenticatedSessionGateway sessions,
            EntityIdGenerator identifiers) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.identifiers = identifiers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse login(@RequestBody LoginRequest request) {
        var password = request.passwordCopy();
        try (var command = new PersonLoginCommand(
                request.tenantCode(),
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

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        sessions.revoke(BearerTokens.require(authorization));
    }
}
