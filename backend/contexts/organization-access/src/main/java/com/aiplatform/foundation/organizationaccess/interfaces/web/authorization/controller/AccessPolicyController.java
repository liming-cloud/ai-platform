package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.accesspolicy.AccessPolicyService;
import com.aiplatform.foundation.organizationaccess.application.accesspolicy.AccessPolicyStore;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.request.MemberProvisioningRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 路径只定位策略与版本；服务端会话决定租户和操作者，严格请求不能覆盖范围。 */
@RestController
@RequestMapping("/api/v1/tenant/access-policies")
public final class AccessPolicyController {
    private final AccessPolicyService service;
    public AccessPolicyController(AccessPolicyService service) { this.service = service; }
    /** 以当前会话范围分页筛选目录，不接受外部租户条件。 */
    @GetMapping
    public PageResult<AccessPolicyStore.Summary> list(TrustedRequestContext context, @RequestParam Map<String, String> query) {
        query(query, Set.of("pageNumber", "pageSize", "q", "status"));
        return service.list(context, page(query), query.getOrDefault("q", ""), query.getOrDefault("status", ""));
    }
    /** 定位本租户策略的最新版本，其他租户资源按不存在处理。 */
    @GetMapping("/{policyId}")
    public AccessPolicyService.View read(TrustedRequestContext context, @PathVariable String policyId, @RequestParam Map<String, String> query) {
        query(query, Set.of());
        return service.read(context, id(policyId), null);
    }
    /** 返回本租户普通成员候选，筛选与总数在同一范围内计算。 */
    @GetMapping("/member-options")
    public PageResult<AccessPolicyStore.MemberChoice> memberChoices(TrustedRequestContext context, @RequestParam Map<String, String> query) {
        query(query, Set.of("pageNumber", "pageSize", "q")); return service.memberChoices(context, page(query), query.getOrDefault("q", ""));
    }
    /** 从拥有者公开契约读取当前已发布助手及其固定知识库关系。 */
    @GetMapping("/assistant-options/{assistantId}")
    public AccessPolicyService.AssistantOption assistantOption(TrustedRequestContext context, @PathVariable String assistantId, @RequestParam Map<String, String> query) {
        query(query, Set.of()); return service.assistantOption(context, id(assistantId));
    }
    /** 分页读取本策略版本，不通过历史版本重放有效授权。 */
    @GetMapping("/{policyId}/versions")
    public PageResult<AccessPolicyService.View> versions(TrustedRequestContext context, @PathVariable String policyId, @RequestParam Map<String, String> query) {
        query(query, Set.of("pageNumber", "pageSize")); return service.versions(context, id(policyId), page(query));
    }
    /** 只读定位本租户策略的指定冻结版本或草稿。 */
    @GetMapping("/{policyId}/versions/{versionId}")
    public AccessPolicyService.View readVersion(TrustedRequestContext context, @PathVariable String policyId, @PathVariable String versionId,
            @RequestParam Map<String, String> query) {
        query(query, Set.of()); return service.read(context, id(policyId), id(versionId));
    }
    /** 建立草稿并绑定授权与租户上限版本；创建不赋予成员权限。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessPolicyService.View create(TrustedRequestContext context, @RequestParam Map<String, String> query, @RequestBody Map<String, Object> body) {
        query(query, Set.of()); fields(body, Set.of("name", "assistantId", "membershipIds", "expectedAuthorizationRevision", "expectedTenantFeatureVersion"));
        if (!(body.get("name") instanceof String name)) throw new IllegalArgumentException("策略名称必须为文本");
        return service.create(context, name, selection(body), version(body, "expectedAuthorizationRevision"), version(body, "expectedTenantFeatureVersion"));
    }
    /** 从指定已发布版本建立新草稿，不覆盖当前有效授权。 */
    @PostMapping("/{policyId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessPolicyService.View newVersion(TrustedRequestContext context, @PathVariable String policyId,
            @RequestParam Map<String, String> query, @RequestBody Map<String, Object> body) {
        query(query, Set.of()); fields(body, Set.of("sourceVersionId", "expectedVersion"));
        return service.newVersion(context, id(policyId), reference(body.get("sourceVersionId")), version(body, "expectedVersion"));
    }
    /** 仅保存匹配版本的草稿，保存后旧预览失效。 */
    @PutMapping("/{policyId}/versions/{versionId}")
    public AccessPolicyService.View save(TrustedRequestContext context, @PathVariable String policyId, @PathVariable String versionId,
            @RequestParam Map<String, String> query, @RequestBody Map<String, Object> body) {
        query(query, Set.of()); fields(body, Set.of("assistantId", "membershipIds", "expectedVersion", "expectedAuthorizationRevision", "expectedTenantFeatureVersion"));
        return service.save(context, id(policyId), id(versionId), selection(body), expected(body));
    }
    /** 根据当前完整授权事实计算预览，版本变化时拒绝生成。 */
    @PostMapping("/{policyId}/versions/{versionId}/preview")
    public AccessPolicyService.View preview(TrustedRequestContext context, @PathVariable String policyId, @PathVariable String versionId,
            @RequestParam Map<String, String> query, @RequestBody Map<String, Object> body) {
        query(query, Set.of()); fields(body, Set.of("expectedVersion", "expectedAuthorizationRevision", "expectedTenantFeatureVersion"));
        return service.preview(context, id(policyId), id(versionId), expected(body));
    }
    /** 复核有效预览并原子发布，审计或会话撤销失败时回滚。 */
    @PostMapping("/{policyId}/versions/{versionId}/publish")
    public AccessPolicyService.View publish(TrustedRequestContext context, @PathVariable String policyId, @PathVariable String versionId,
            @RequestParam Map<String, String> query, @RequestBody Map<String, Object> body) {
        query(query, Set.of()); fields(body, Set.of("expectedVersion", "expectedAuthorizationRevision", "expectedTenantFeatureVersion"));
        return service.publish(context, id(policyId), id(versionId), expected(body));
    }
    private static AccessPolicyService.Selection selection(Map<String, Object> body) {
        if (!(body.get("membershipIds") instanceof List<?> values)) throw new IllegalArgumentException("普通成员选择必须为列表");
        var ids = new HashSet<Long>();
        for (var value : values) if (!ids.add(reference(value))) throw new IllegalArgumentException("成员引用不能重复");
        return new AccessPolicyService.Selection(reference(body.get("assistantId")), Set.copyOf(ids));
    }
    private static AccessPolicyService.Expected expected(Map<String, Object> body) {
        return new AccessPolicyService.Expected(version(body, "expectedVersion"), version(body, "expectedAuthorizationRevision"), version(body, "expectedTenantFeatureVersion"));
    }
    private static long version(Map<String, Object> body, String field) { return MemberProvisioningRequest.version(body.get(field)); }
    private static void fields(Map<String, Object> body, Set<String> expected) {
        if (body == null || !body.keySet().equals(expected)) throw new IllegalArgumentException("策略字段缺失或包含未知字段");
    }
    private static void query(Map<String, String> query, Set<String> allowed) {
        if (!allowed.containsAll(query.keySet())) throw new IllegalArgumentException("策略不接受外部范围或未知参数");
    }
    private static PageQuery page(Map<String, String> query) {
        return new PageQuery(id(query.getOrDefault("pageNumber", "1")), id(query.getOrDefault("pageSize", "20")));
    }
    private static long reference(Object value) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("资源引用必须为正整数文本");
        return id(text);
    }
    private static long id(String value) {
        if (!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("资源引用必须为正整数文本");
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("资源引用超出允许范围"); }
    }
}
