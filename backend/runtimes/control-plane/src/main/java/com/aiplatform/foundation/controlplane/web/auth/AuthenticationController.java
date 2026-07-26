package com.aiplatform.foundation.controlplane.web.auth;

import com.aiplatform.foundation.controlplane.web.TraceIds;
import com.aiplatform.foundation.organizationaccess.adapter.out.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.organizationaccess.api.PersonAuthenticationUseCase;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.PersonLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(PersonAuthenticationUseCase.class)
public class AuthenticationController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_TOKEN_LENGTH = 512;

    private final PersonAuthenticationUseCase authentication;
    private final AuthenticatedSessionGateway sessions;

    public AuthenticationController(
            PersonAuthenticationUseCase authentication,
            AuthenticatedSessionGateway sessions) {
        this.authentication = authentication;
        this.sessions = sessions;
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
                TraceIds.next())) {
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
        sessions.revoke(requireBearerToken(authorization));
    }

    private static String requireBearerToken(String authorization) {
        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() <= BEARER_PREFIX.length()) {
            throw new InvalidAuthenticatedSessionException();
        }
        var token = authorization.substring(BEARER_PREFIX.length());
        if (token.isBlank()
                || token.length() > MAX_TOKEN_LENGTH
                || !token.equals(token.trim())
                || token.indexOf(' ') >= 0) {
            throw new InvalidAuthenticatedSessionException();
        }
        return token;
    }
}
