package com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller;

import com.aiplatform.foundation.organizationaccess.application.member.service.MemberAssistantSettingsService.MemberAssistantSettingsView;
import com.aiplatform.foundation.organizationaccess.application.member.service.MemberAssistantSettingsService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.request.MemberProvisioningRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

/** 只定位当前租户成员并解析严格助手集合；不接受身份或功能覆盖。 */
@RestController
@RequestMapping("/api/v1/tenant/members/{membershipId}/assistants")
public final class MemberAssistantSettingsController {
    private final MemberAssistantSettingsService service;
    public MemberAssistantSettingsController(MemberAssistantSettingsService service) { this.service = service; }

    /** 读取独立助手授权及全租户 revision。 */
    @GetMapping
    public MemberAssistantSettingsView read(TrustedRequestContext context, @PathVariable String membershipId,
            @RequestParam Map<String, String> query) {
        reject(query);
        return service.read(context, id(membershipId));
    }
    /** 严格全量替换，不因未知字段或数字转换扩大范围。 */
    @PutMapping
    public MemberAssistantSettingsView save(TrustedRequestContext context, @PathVariable String membershipId,
            @RequestParam Map<String, String> query, @RequestBody Map<String, Object> body) {
        reject(query);
        if (body == null || !body.keySet().equals(Set.of("assistantIds", "expectedAuthorizationRevision"))
                || !(body.get("assistantIds") instanceof List<?> values)) throw new IllegalArgumentException("成员助手字段不合法");
        var selected = new HashSet<Long>();
        for (var value : values) {
            if (!(value instanceof String text) || !selected.add(id(text))) throw new IllegalArgumentException("助手引用无效或重复");
        }
        return service.save(context, id(membershipId), Set.copyOf(selected), MemberProvisioningRequest.version(body.get("expectedAuthorizationRevision")));
    }
    private static void reject(Map<String, String> query) {
        if (!query.isEmpty()) throw new IllegalArgumentException("成员助手不接受外部范围参数");
    }
    private static long id(String value) {
        if (!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("资源引用必须为正整数文本");
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("资源引用超出允许范围"); }
    }
}
