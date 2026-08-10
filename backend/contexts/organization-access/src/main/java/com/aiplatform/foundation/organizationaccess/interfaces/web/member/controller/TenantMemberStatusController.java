package com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller;

import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberStatusApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.request.TenantMemberEstablishmentRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.response.TenantMemberStatusResponse;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.response.TenantMemberSummaryResponse;
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

    private final TenantMemberStatusApplicationService members;
    private final TenantMemberDirectoryApplicationService directory;
    private final TenantMemberEstablishmentApplicationService establishment;

    /** 构造成员状态 HTTP 入口。 */
    public TenantMemberStatusController(
            TenantMemberStatusApplicationService members,
            TenantMemberDirectoryApplicationService directory,
            TenantMemberEstablishmentApplicationService establishment) {
        this.members = Objects.requireNonNull(members, "成员状态服务不能为空");
        this.directory = Objects.requireNonNull(directory, "成员目录服务不能为空");
        this.establishment = Objects.requireNonNull(establishment, "成员建立服务不能为空");
    }

    /** 分页读取当前令牌所属租户的成员目录。 */
    @GetMapping
    public PageResult<TenantMemberSummaryResponse> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        var page = directory.list(context, new PageQuery(pageNumber, pageSize));
        return new PageResult<>(
                page.items().stream().map(TenantMemberSummaryResponse::from).toList(),
                page.pageNumber(),
                page.pageSize(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }

    /** 建立当前租户的普通成员资格，不授予任何隐式业务权限。 */
    @PostMapping
    public ResponseEntity<TenantMemberStatusResponse> establish(
            TrustedRequestContext context,
            @RequestBody Map<String, Object> payload) {
        var request = TenantMemberEstablishmentRequest.from(payload);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TenantMemberStatusResponse.from(establishment.establish(
                        context, request.subjectId())));
    }

    /** 停用成员并撤销其已签发的租户会话。 */
    @PostMapping("/{membershipId}/disable")
    public TenantMemberStatusResponse disable(
            TrustedRequestContext context,
            @PathVariable String membershipId) {
        return TenantMemberStatusResponse.from(members.disable(
                context, parseMembershipId(membershipId)));
    }

    /** 启用成员并撤销其已签发的租户会话。 */
    @PostMapping("/{membershipId}/enable")
    public TenantMemberStatusResponse enable(
            TrustedRequestContext context,
            @PathVariable String membershipId) {
        return TenantMemberStatusResponse.from(members.enable(
                context, parseMembershipId(membershipId)));
    }

    private static long parseMembershipId(String membershipId) {
        try {
            return Long.parseLong(membershipId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("成员标识必须是十进制正整数", exception);
        }
    }
}
