package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorSummary;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorCredentialService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorQueryService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantAdministratorAssignmentRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantAdministratorPasswordResetRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantAdministratorReplacementRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantAdministratorVersionRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response.TenantAdministratorResponse;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 平台独占的租户管理员目录与生命周期 HTTP 入口。 */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/administrators")
public final class PlatformTenantAdministratorController {

    private final PlatformTenantAdministratorApplicationService commands;
    private final PlatformTenantAdministratorQueryService queries;
    private final PlatformTenantAdministratorCredentialService credentials;

    public PlatformTenantAdministratorController(
            PlatformTenantAdministratorApplicationService commands,
            PlatformTenantAdministratorQueryService queries,
            PlatformTenantAdministratorCredentialService credentials) {
        this.commands = Objects.requireNonNull(commands, "租户管理员命令服务不能为空");
        this.queries = Objects.requireNonNull(queries, "租户管理员查询服务不能为空");
        this.credentials = Objects.requireNonNull(credentials, "租户管理员凭据服务不能为空");
    }

    /** 分页读取目标租户的管理员安全摘要。 */
    @GetMapping
    public PageResult<TenantAdministratorSummary> list(
            PlatformRequestContext platform,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return queries.list(platform, tenantId, q, status, new PageQuery(pageNumber, pageSize));
    }

    /** 搜索可分配的活动人员账户，不返回凭据、权限或会话事实。 */
    @GetMapping("/candidates")
    public PageResult<PlatformAccountCandidate> candidates(
            PlatformRequestContext platform,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return queries.candidates(platform, tenantId, q, new PageQuery(pageNumber, pageSize));
    }

    /** 分配新管理员或原位提升既有普通成员。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantAdministratorResponse assign(
            PlatformRequestContext platform,
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> payload) {
        var request = TenantAdministratorAssignmentRequest.from(payload);
        return TenantAdministratorResponse.from(
                commands.assign(platform, tenantId, request.accountReferenceValue()));
    }

    /** 停用非最后一个启用管理员。 */
    @PostMapping("/{membershipReference}/disable")
    public TenantAdministratorResponse disable(
            PlatformRequestContext platform,
            @PathVariable String tenantId,
            @PathVariable String membershipReference,
            @RequestBody Map<String, Object> payload) {
        var request = TenantAdministratorVersionRequest.from(payload);
        return TenantAdministratorResponse.from(commands.disable(
                platform, tenantId, parseMembershipReference(membershipReference),
                request.expectedVersion()));
    }

    /** 恢复已停用管理员，并重新校验平台／租户人员范围互斥。 */
    @PostMapping("/{membershipReference}/restore")
    public TenantAdministratorResponse restore(
            PlatformRequestContext platform,
            @PathVariable String tenantId,
            @PathVariable String membershipReference,
            @RequestBody Map<String, Object> payload) {
        var request = TenantAdministratorVersionRequest.from(payload);
        return TenantAdministratorResponse.from(commands.restore(
                platform, tenantId, parseMembershipReference(membershipReference),
                request.expectedVersion()));
    }

    /** 原子分配新管理员后停用旧管理员，禁止零管理员中间状态。 */
    @PostMapping("/{membershipReference}/replace")
    public TenantAdministratorResponse replace(
            PlatformRequestContext platform,
            @PathVariable String tenantId,
            @PathVariable String membershipReference,
            @RequestBody Map<String, Object> payload) {
        var request = TenantAdministratorReplacementRequest.from(payload);
        return TenantAdministratorResponse.from(commands.replace(
                platform,
                tenantId,
                parseMembershipReference(membershipReference),
                request.expectedVersion(),
                request.replacementAccountReferenceValue()));
    }

    /** 按目标租户的管理员成员资格重置全局密码，响应不包含任何凭据。 */
    @PostMapping("/{membershipReference}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            PlatformRequestContext platform,
            @PathVariable String tenantId,
            @PathVariable String membershipReference,
            @RequestBody Map<String, Object> payload) {
        var request = TenantAdministratorPasswordResetRequest.from(payload);
        try {
            credentials.resetPassword(platform, tenantId,
                    parseMembershipReference(membershipReference), request.expectedVersion(), request.passwordCopy());
        } finally {
            request.clear();
        }
    }

    private static long parseMembershipReference(String reference) {
        try {
            return Long.parseLong(reference);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("成员引用必须是十进制正整数", exception);
        }
    }
}
