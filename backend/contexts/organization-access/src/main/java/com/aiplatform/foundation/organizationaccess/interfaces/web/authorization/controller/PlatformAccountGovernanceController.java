package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountGovernanceApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.PlatformAccountPasswordResetRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response.PlatformAccountSummaryResponse;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台账户高风险治理入口。
 *
 * <p>平台操作者及其权限只能由 `PlatformRequestContext` 恢复；路径主体标识只是受管账户资源，响应不披露
 * 凭据、会话或权限快照。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/accounts")
public final class PlatformAccountGovernanceController {

    private final PlatformAccountGovernanceApplicationService accounts;
    private final PlatformAccountDirectoryApplicationService directory;

    /** 构造平台账户治理 HTTP 入口。 */
    public PlatformAccountGovernanceController(
            PlatformAccountGovernanceApplicationService accounts,
            PlatformAccountDirectoryApplicationService directory) {
        this.accounts = Objects.requireNonNull(accounts, "平台账户治理服务不能为空");
        this.directory = Objects.requireNonNull(directory, "平台账户目录服务不能为空");
    }

    @GetMapping
    public PageResult<PlatformAccountSummaryResponse> list(
            PlatformRequestContext platform,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        var page = directory.list(platform, new PageQuery(pageNumber, pageSize));
        return new PageResult<>(
                page.items().stream().map(PlatformAccountSummaryResponse::from).toList(),
                page.pageNumber(),
                page.pageSize(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }

    /** 禁用目标账户，并使该主体的全部平台及租户会话失效。 */
    @PostMapping("/{subjectId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(
            PlatformRequestContext platform,
            @PathVariable String subjectId) {
        accounts.disable(platform, parseSubjectId(subjectId));
    }

    /** 启用路径目标账户，并使目标主体旧会话全部失效。 */
    @PostMapping("/{subjectId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(
            PlatformRequestContext platform,
            @PathVariable String subjectId) {
        accounts.enable(platform, parseSubjectId(subjectId));
    }

    /** 重置路径目标账户的凭据，并撤销该主体全部旧范围会话。 */
    @PostMapping("/{subjectId}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            PlatformRequestContext platform,
            @PathVariable String subjectId,
            @RequestBody Map<String, Object> payload) {
        var request = PlatformAccountPasswordResetRequest.from(payload);
        try {
            accounts.resetPassword(platform, parseSubjectId(subjectId), request.passwordCopy());
        } finally {
            request.clear();
        }
    }

    private static long parseSubjectId(String subjectId) {
        try {
            return Long.parseLong(subjectId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("主体标识必须是十进制正整数", exception);
        }
    }
}
