package com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller;

import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberFeatureSettingsView;
import com.aiplatform.foundation.organizationaccess.application.member.service.MemberFeatureSettingsService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.request.MemberFeatureSettingsRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 当前租户普通成员的独立功能入口；路径只定位目标，不接受客户端范围覆盖。 */
@RestController
@RequestMapping("/api/v1/tenant/members/{membershipId}/features")
public final class MemberFeatureSettingsController {
    private final MemberFeatureSettingsService service;
    public MemberFeatureSettingsController(MemberFeatureSettingsService service) { this.service = service; }

    /** 返回保存值、租户上限及独立版本；读取没有业务写入。 */
    @GetMapping
    public MemberFeatureSettingsView read(TrustedRequestContext context, @PathVariable String membershipId, @RequestParam Map<String, String> query) {
        rejectQuery(query);
        return service.read(context, id(membershipId));
    }

    /** 完整替换功能，不能将其他设置页的草稿混入同一请求。 */
    @PutMapping
    public MemberFeatureSettingsView save(TrustedRequestContext context, @PathVariable String membershipId,
            @RequestParam Map<String, String> query, @RequestBody Map<String, Object> body) {
        rejectQuery(query);
        return service.save(context, id(membershipId), MemberFeatureSettingsRequest.from(body));
    }

    private static void rejectQuery(Map<String, String> query) {
        if (!query.isEmpty()) throw new IllegalArgumentException("成员功能不接受外部范围参数");
    }
    private static long id(String value) {
        if (!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("成员标识必须为十进制正整数");
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("成员标识超出允许范围"); }
    }
}
