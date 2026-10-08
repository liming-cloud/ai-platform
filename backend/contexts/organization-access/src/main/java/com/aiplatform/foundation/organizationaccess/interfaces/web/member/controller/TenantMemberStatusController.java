package com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller;

import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.response.TenantMemberSummaryResponse;
import com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountView;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberCreateOptions;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.request.MemberProvisioningRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.request.MemberPasswordResetRequest;
import org.springframework.web.bind.annotation.RequestHeader;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 由 token 恢复的当前租户上下文管理成员状态。
 *
 * <p>租户、当前主体和会话不得作为接口参数；路径成员标识仅表示当前租户内的受管成员资源。</p>
 */
@RestController
@RequestMapping("/api/v1/tenant/members")
public final class TenantMemberStatusController {

    private final TenantMemberDirectoryApplicationService directory;
    private final MemberAccountApplicationService accounts;

    /** 构造成员状态 HTTP 入口。 */
    public TenantMemberStatusController(
            TenantMemberDirectoryApplicationService directory,
            MemberAccountApplicationService accounts) {
        this.directory = Objects.requireNonNull(directory, "成员目录服务不能为空");
        this.accounts = Objects.requireNonNull(accounts, "成员账户服务不能为空");
    }

    /** 分页读取当前令牌所属租户的成员目录。 */
    @GetMapping
    public PageResult<TenantMemberSummaryResponse> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status) {
        accounts.requireAdministrator(context);
        var page = directory.list(context, new PageQuery(pageNumber, pageSize), q, status);
        return new PageResult<>(
                page.items().stream().map(TenantMemberSummaryResponse::from).toList(),
                page.pageNumber(),
                page.pageSize(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }

    /** 读取开户上限和当前独立授权版本。 */
    @GetMapping("/create-options")
    public MemberCreateOptions createOptions(TrustedRequestContext context) { return accounts.options(context); }

    /** 只读账户详情，禁止返回全局主体标识或凭据。 */
    @GetMapping("/{membershipId}")
    public MemberAccountView account(TrustedRequestContext context, @PathVariable String membershipId) {
        return accounts.account(context, parseMembershipId(membershipId));
    }

    /** 一次建立新身份、普通资格、固定角色及初始授权。 */
    @PostMapping
    public ResponseEntity<MemberAccountView> establish(
            TrustedRequestContext context,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody Map<String, Object> payload) {
        try (var request = MemberProvisioningRequest.from(payload)) {
            return ResponseEntity.status(HttpStatus.CREATED).body(accounts.create(context,
                    request.command(), request.passwordCopy(), key));
        }
    }

    /** 停用成员并撤销其已签发的租户会话。 */
    @PostMapping("/{membershipId}/disable")
    public MemberAccountView disable(
            TrustedRequestContext context,
            @PathVariable String membershipId, @RequestBody Map<String, Object> payload) {
        return accounts.status(context, parseMembershipId(membershipId), expectedVersion(payload), false);
    }

    /** 启用成员并撤销其已签发的租户会话。 */
    @PostMapping("/{membershipId}/enable")
    public MemberAccountView enable(
            TrustedRequestContext context,
            @PathVariable String membershipId, @RequestBody Map<String, Object> payload) {
        return accounts.status(context, parseMembershipId(membershipId), expectedVersion(payload), true);
    }

    /** 唯一归属裁决在服务器执行，密码请求及服务副本各自清理。 */
    @PostMapping("/{membershipId}/password-reset")
    public ResponseEntity<Void> resetPassword(TrustedRequestContext context, @PathVariable String membershipId,
            @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> payload) {
        try (var request = MemberPasswordResetRequest.from(payload)) {
            accounts.resetPassword(context, parseMembershipId(membershipId), request.expectedVersion(),
                    request.expectedIdentityVersion(), request.passwordCopy(), key);
            return ResponseEntity.noContent().build();
        }
    }

    private static long expectedVersion(Map<String, Object> payload) {
        if (!payload.keySet().equals(java.util.Set.of("expectedVersion"))) throw new IllegalArgumentException("资格变更字段不合法");
        return MemberProvisioningRequest.version(payload.get("expectedVersion"));
    }

    private static long parseMembershipId(String membershipId) {
        try {
            return Long.parseLong(membershipId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("成员标识必须是十进制正整数", exception);
        }
    }
}
