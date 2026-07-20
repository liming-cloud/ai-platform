package com.aiplatform.infra.satoken.web;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.error.SaErrorCode;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.aiplatform.infra.satoken.web.permission.config.RoutePermissionProperties;
import com.aiplatform.infra.satoken.web.permission.model.RoutePermissionRule;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@AutoConfiguration
@EnableConfigurationProperties(RoutePermissionProperties.class)
public class SaTokenWebSecurityAutoConfiguration {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACTIVATION_PATH = "/api/v1/auth/activate";
    private static final String HEALTH_PATH = "/api/v1/health";
    private static final String SESSION_INVALID_CODE = "AIP-IAM-SESSIONINVALID";
    private static final String SESSION_INVALID_MESSAGE = "登录态无效或已过期";
    private static final String PERMISSION_DENIED_CODE = "AIP-IAM-PERMISSIONDENIED";
    private static final String PERMISSION_DENIED_MESSAGE = "当前账号缺少访问该接口的权限";
    private static final String AUTHORIZATION_FAILED_CODE = "AIP-IAM-AUTHFAILED";
    private static final String AUTHORIZATION_FAILED_MESSAGE = "认证或授权失败";

    private final JsonMapper objectMapper = new JsonMapper();

    @Bean
    public FilterRegistrationBean<SaTokenPrincipalFilter> saTokenPrincipalFilter() {
        FilterRegistrationBean<SaTokenPrincipalFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SaTokenPrincipalFilter());
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<SaServletFilter> routePermissionFilter(RoutePermissionProperties properties) {
        List<RoutePermissionRule> routePermissions = properties.routePermissions();
        StpLogic stpLogic = StpUtil.getStpLogic();
        FilterRegistrationBean<SaServletFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SaServletFilter()
                .addInclude("/api/v1/**")
                .addExclude(LOGIN_PATH, ACTIVATION_PATH, HEALTH_PATH)
                .setBeforeAuth(ignored -> clearSecurityContext())
                .setAuth(ignored -> {
                    SaRequest request = SaHolder.getRequest();
                    checkRoutePermissions(
                            request.getMethod(),
                            request.getRequestPath(),
                            routePermissions,
                            stpLogic
                    );
                })
                .setError(this::writeErrorResponse));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(2);
        return registration;
    }

    private static void clearSecurityContext() {
        TenantContext.clear();
        PrincipalContext.clear();
    }

    static void checkRoutePermissions(
            String requestMethod,
            String requestPath,
            List<RoutePermissionRule> routePermissions,
            StpLogic stpLogic
    ) {
        stpLogic.checkLogin();
        RoutePermissionRule matchedRule = routePermissions.stream()
                .filter(rule -> SaRouter.isMatch(SaHttpMethod.toEnumArray(rule.method()), requestMethod))
                .filter(rule -> SaRouter.isMatch(rule.pattern(), requestPath))
                .findFirst()
                .orElseThrow(() -> permissionRuleMissing(stpLogic));
        stpLogic.checkPermission(matchedRule.permission());
    }

    private static NotPermissionException permissionRuleMissing(StpLogic stpLogic) {
        NotPermissionException exception = new NotPermissionException(
                "route-permission:unconfigured",
                stpLogic.getLoginType()
        );
        exception.setCode(SaErrorCode.CODE_11051);
        return exception;
    }

    private String writeErrorResponse(Throwable error) {
        if (error instanceof NotLoginException) {
            return writeError(HttpStatus.UNAUTHORIZED, SESSION_INVALID_CODE, SESSION_INVALID_MESSAGE);
        }
        if (error instanceof NotPermissionException) {
            return writeError(HttpStatus.FORBIDDEN, PERMISSION_DENIED_CODE, PERMISSION_DENIED_MESSAGE);
        }
        return writeError(HttpStatus.INTERNAL_SERVER_ERROR, AUTHORIZATION_FAILED_CODE, AUTHORIZATION_FAILED_MESSAGE);
    }

    private String writeError(HttpStatus status, String code, String message) {
        SaHolder.getResponse()
                .setStatus(status.value())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        try {
            return objectMapper.writeValueAsString(ApiResponse.failure(code, message));
        } catch (JacksonException exception) {
            return "{\"header\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"},\"body\":null}";
        }
    }
}
