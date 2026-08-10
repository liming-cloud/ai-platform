package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleLifecycleApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.TenantCustomRoleCreationRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.TenantCustomRolePermissionChangeRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response.TenantCustomRoleResponse;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response.TenantCustomRoleSummaryResponse;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 由 token 恢复的当前租户上下文创建自定义角色。 */
@RestController
@RequestMapping("/api/v1/tenant/custom-roles")
public final class TenantCustomRoleController {

    private final TenantCustomRoleApplicationService roles;
    private final TenantCustomRoleDirectoryApplicationService directory;
    private final TenantCustomRoleLifecycleApplicationService lifecycle;

    public TenantCustomRoleController(
            TenantCustomRoleApplicationService roles,
            TenantCustomRoleDirectoryApplicationService directory,
            TenantCustomRoleLifecycleApplicationService lifecycle) {
        this.roles = Objects.requireNonNull(roles, "租户自定义角色服务不能为空");
        this.directory = Objects.requireNonNull(directory, "租户自定义角色目录服务不能为空");
        this.lifecycle = Objects.requireNonNull(lifecycle, "租户自定义角色生命周期服务不能为空");
    }

    /** 分页读取当前令牌所属租户的自定义角色目录。 */
    @GetMapping
    public PageResult<TenantCustomRoleSummaryResponse> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        var page = directory.list(context, new PageQuery(pageNumber, pageSize));
        return new PageResult<>(
                page.items().stream().map(TenantCustomRoleSummaryResponse::from).toList(),
                page.pageNumber(), page.pageSize(), page.totalElements(), page.totalPages(),
                page.hasPrevious(), page.hasNext());
    }

    /**
     * 创建当前租户拥有的自定义角色。
     *
     * <p>租户、当前主体、权限版本和会话均只从可信 token 上下文恢复，不能由请求体伪造。</p>
     */
    @PostMapping
    public ResponseEntity<TenantCustomRoleResponse> create(
            TrustedRequestContext context,
            @RequestBody Map<String, Object> payload) {
        var request = TenantCustomRoleCreationRequest.from(payload);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TenantCustomRoleResponse.from(roles.create(context, request.toCommand())));
    }

    /** 修改当前租户活动自定义角色的权限集合。 */
    @PutMapping("/{customRoleId}/permissions")
    public ResponseEntity<TenantCustomRoleResponse> changePermissions(
            TrustedRequestContext context,
            @PathVariable long customRoleId,
            @RequestBody Map<String, Object> payload) {
        var request = TenantCustomRolePermissionChangeRequest.from(payload);
        return ResponseEntity.ok(TenantCustomRoleResponse.from(
                lifecycle.changePermissions(context, request.toCommand(customRoleId))));
    }

    /** 停用当前租户一项自定义角色，并使受影响成员旧会话失效。 */
    @PostMapping("/{customRoleId}/disable")
    public ResponseEntity<TenantCustomRoleResponse> disable(
            TrustedRequestContext context,
            @PathVariable long customRoleId) {
        return ResponseEntity.ok(TenantCustomRoleResponse.from(
                lifecycle.disable(context, new com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleDisableCommand(customRoleId))));
    }
}
