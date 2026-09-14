package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Objects;

/** 单表配额持久化映射，框架可变字段不跨出基础设施边界。 */
@TableName("organization_access.tenant_quota")
public class TenantQuotaEntity {

    @TableId(type = IdType.INPUT)
    private Long tenantId;
    private Integer knowledgeBases;
    private Integer documentsPerKnowledgeBase;
    private Long bytesPerKnowledgeBase;
    private Long maxFileBytes;
    private Long maxTextBytes;
    private Long version;

    /** 从领域快照生成写入行，保留应用层确定的租户标识与版本。 */
    public static TenantQuotaEntity from(TenantQuota quota) {
        Objects.requireNonNull(quota, "资源配额不能为空");
        var entity = new TenantQuotaEntity();
        entity.tenantId = quota.tenantId().value();
        entity.knowledgeBases = quota.knowledgeBases();
        entity.documentsPerKnowledgeBase = quota.documentsPerKnowledgeBase();
        entity.bytesPerKnowledgeBase = quota.bytesPerKnowledgeBase();
        entity.maxFileBytes = quota.maxFileBytes();
        entity.maxTextBytes = quota.maxTextBytes();
        entity.version = quota.version();
        return entity;
    }

    /** 恢复快照时重新验证领域不变量，损坏行不能成为有效配置。 */
    public TenantQuota toDomain() {
        return new TenantQuota(new TenantId(tenantId), knowledgeBases, documentsPerKnowledgeBase,
                bytesPerKnowledgeBase, maxFileBytes, maxTextBytes, version);
    }

    /** 供持久化框架读取租户主键。 */
    public Long getTenantId() {
        return tenantId;
    }

    /** 供持久化框架填充租户主键。 */
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    /** 供持久化框架读取知识库数量。 */
    public Integer getKnowledgeBases() {
        return knowledgeBases;
    }

    /** 供持久化框架填充知识库数量。 */
    public void setKnowledgeBases(Integer knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    /** 供持久化框架读取每库文档数量。 */
    public Integer getDocumentsPerKnowledgeBase() {
        return documentsPerKnowledgeBase;
    }

    /** 供持久化框架填充每库文档数量。 */
    public void setDocumentsPerKnowledgeBase(Integer documentsPerKnowledgeBase) {
        this.documentsPerKnowledgeBase = documentsPerKnowledgeBase;
    }

    /** 供持久化框架读取每库内容容量。 */
    public Long getBytesPerKnowledgeBase() {
        return bytesPerKnowledgeBase;
    }

    /** 供持久化框架填充每库内容容量。 */
    public void setBytesPerKnowledgeBase(Long bytesPerKnowledgeBase) {
        this.bytesPerKnowledgeBase = bytesPerKnowledgeBase;
    }

    /** 供持久化框架读取单文件大小。 */
    public Long getMaxFileBytes() {
        return maxFileBytes;
    }

    /** 供持久化框架填充单文件大小。 */
    public void setMaxFileBytes(Long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    /** 供持久化框架读取粘贴文本大小。 */
    public Long getMaxTextBytes() {
        return maxTextBytes;
    }

    /** 供持久化框架填充粘贴文本大小。 */
    public void setMaxTextBytes(Long maxTextBytes) {
        this.maxTextBytes = maxTextBytes;
    }

    /** 供持久化框架读取配置版本。 */
    public Long getVersion() {
        return version;
    }

    /** 供持久化框架填充配置版本。 */
    public void setVersion(Long version) {
        this.version = version;
    }
}
